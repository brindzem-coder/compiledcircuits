package com.example.compiledcircuits.client;

import com.example.compiledcircuits.networking.NetworkActionC2SPacket;
import com.example.compiledcircuits.networking.NetworkBulkActionC2SPacket;
import java.util.*;
import java.util.function.*;

/** Captures action targets and owns the only dispatch boundary. Server remains authoritative. */
final class ManagerActions {
    interface Transport {
        void single(NetworkActionC2SPacket.Action action,int id,String value);
        void bulk(NetworkBulkActionC2SPacket.BulkAction action,Set<Integer> ids,int target);
        void highlight(Set<Integer> ids);
        default void refresh() {}
    }
    private final ManagerModel model;
    private final Transport transport;
    private final LongSupplier clock;
    private final BooleanSupplier contextValid;
    private final Map<Object,Long> pending=new HashMap<>();
    private boolean closed;
    private int refreshTicks;
    private static final long RETRY_NANOS=2_000_000_000L;
    private record Request(Object action,Set<Integer> ids,int target,String value) {}
    ManagerActions(ManagerModel model,Transport transport,LongSupplier clock,BooleanSupplier contextValid) {
        this.model=model;this.transport=transport;this.clock=clock;this.contextValid=contextValid;
    }
    boolean active() { return !closed && contextValid.getAsBoolean(); }
    private boolean begin(Request request) {
        if(!active())return false;
        long now=clock.getAsLong(); pending.values().removeIf(time->now-time>=RETRY_NANOS);
        if(pending.containsKey(request))return false;
        pending.put(request,now);return true;
    }
    // Replies have no request IDs. A list refresh releases the short duplicate guard, not a success claim.
    void refreshed() { pending.clear(); }
    void tick() { if (active() && ++refreshTicks >= 40) { refreshTicks = 0; transport.refresh(); } }
    void close() { closed=true;pending.clear(); }
    final class Edit {
        private final NetworkActionC2SPacket.Action action;
        private final int id;
        private boolean finished;
        Edit(NetworkActionC2SPacket.Action action,int id) {this.action=action;this.id=id;}
        boolean save(String value) {
            if(finished)return false;finished=true;
            boolean folder=action!=NetworkActionC2SPacket.Action.RENAME_NETWORK;
            if(folder ? model.folder(id)==null : model.network(id)==null)return false;
            if(!begin(new Request(action,Set.of(id),0,value)))return false;
            transport.single(action,id,value);return true;
        }
        void cancel() { finished=true; }
    }
    Edit edit(NetworkActionC2SPacket.Action action,int id) { return new Edit(action,id); }
    boolean bulk(NetworkBulkActionC2SPacket.BulkAction action,Collection<Integer> selected,int target) {
        var ids=Collections.unmodifiableSet(new LinkedHashSet<>(selected));if(ids.isEmpty())return false;
        boolean folders=action==NetworkBulkActionC2SPacket.BulkAction.MOVE_FOLDERS
                || action==NetworkBulkActionC2SPacket.BulkAction.DELETE_FOLDERS;
        for(int id:ids)if(folders ? id==0||model.folder(id)==null : model.network(id)==null)return false;
        if((action==NetworkBulkActionC2SPacket.BulkAction.MOVE_NETWORKS
                ||action==NetworkBulkActionC2SPacket.BulkAction.MOVE_FOLDERS)&&model.folder(target)==null)return false;
        if(!begin(new Request(action,ids,target,"")))return false;
        transport.bulk(action,ids,target);return true;
    }
    boolean highlight(Collection<Integer> selected) {
        var ids=Collections.unmodifiableSet(new LinkedHashSet<>(selected));
        if(ids.isEmpty()||ids.stream().anyMatch(id->model.network(id)==null)
                ||!begin(new Request("highlight",ids,0,"")))return false;
        transport.highlight(ids);return true;
    }
}
