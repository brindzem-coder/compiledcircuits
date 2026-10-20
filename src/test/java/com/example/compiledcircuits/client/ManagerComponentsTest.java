package com.example.compiledcircuits.client;

import java.util.*;
import com.example.compiledcircuits.networking.*;
import com.example.compiledcircuits.network.CircuitElementType;
import net.minecraft.core.BlockPos;
import static com.example.compiledcircuits.client.ManagerState.*;
import static com.example.compiledcircuits.networking.NetworkBulkActionC2SPacket.BulkAction.*;
import static com.example.compiledcircuits.networking.NetworkActionC2SPacket.Action.*;

/** Behavioral checks run without Minecraft, Screen or a renderer instance. */
public final class ManagerComponentsTest {
    private static int checks;
    private static void check(boolean condition,String message) { checks++;if(!condition)throw new AssertionError(message); }
    private static NetworkListS2CPacket.Entry network(int id,String name,int folder) {
        return new NetworkListS2CPacket.Entry(id,name,folder,"minecraft:overworld",false,10,1,1);
    }
    private static NetworkListS2CPacket.FolderEntry folder(int id,String name,int parent) {
        return new NetworkListS2CPacket.FolderEntry(id,name,parent);
    }
    private static BrokenElementListS2CPacket.Entry broken(int network,int element) {
        return new BrokenElementListS2CPacket.Entry(network,"N"+network,0,element,"minecraft:overworld",
                new BlockPos(network,64,element),CircuitElementType.WIRE,"compiledcircuits:basic_wire","minecraft:air",1);
    }
    public static void main(String[] args) {
        modelAndSelection(); invalidTrees(); brokenSelection(); controller(); geometry(); scale();
        System.out.println("Manager component checks passed: "+checks);
    }
    private static void modelAndSelection() {
        var state=new ManagerState();var model=new ManagerModel(state);
        var folders=List.of(folder(7,"Beta",0),folder(3,"alpha",0),folder(9,"Nested",3));
        var entries=new ArrayList<>(List.of(network(8,"beta",0),network(7,"Alpha",0),network(11,"Alpha",9)));
        model.update(entries,folders);entries.clear();
        check(model.entries.size()==3,"snapshot copies mutable caller list");
        check(model.visibleFolderRows.stream().map(r->r.node().getId()).toList().equals(List.of(0,3,9,7)),"tree name order and nesting");
        check(model.visibleNetworks.stream().map(NetworkListS2CPacket.Entry::id).toList().equals(List.of(7,8)),"network ID order");
        state.selectItem(SelectionType.FOLDERS,7,false);
        state.selectItem(SelectionType.NETWORKS,7,true);
        check(state.selectedFolderIds.isEmpty()&&state.selectedNetworkIds.equals(Set.of(7)),"typed ID namespaces do not alias");
        state.selectItem(SelectionType.NETWORKS,8,true);
        check(state.selectedNetworkIds.equals(Set.of(7,8)),"shift toggle adds");
        state.selectItem(SelectionType.NETWORKS,7,true);
        check(state.selectedNetworkIds.equals(Set.of(8)),"shift toggle removes");
        state.addPaint(SelectionType.NETWORKS,7);state.addPaint(SelectionType.NETWORKS,7);
        check(state.selectedNetworkIds.equals(Set.of(7,8)),"paint is idempotent add, not repeated toggle");
        state.selectItem(SelectionType.FOLDERS,0,false);
        check(state.selectedNetworkIds.equals(Set.of(7,8)),"root is not mutable selection");
        state.searchText="  ALP  ";model.rebuildSearchResults();
        check(model.searchResults.stream().map(ManagerModel.SearchResult::id).toList().equals(List.of(3,7,11)),"search type-name-ID order, trim/case");
        check(state.selectedNetworkIds.contains(8),"hidden selection preserved while typing");
        state.searchFilter=SearchFilter.FOLDERS;state.clearSelection();model.rebuildSearchResults();
        check(model.searchResults.size()==1&&state.selectedNetworkIds.isEmpty(),"filter and explicit selection reset");
        state.searchText="missing";model.rebuildSearchResults();check(model.searchResults.isEmpty(),"empty search result");
        model.toggleFolder(3);check(model.visibleFolderRows.size()==3,"collapse hides descendants");
        model.expandPathToFolder(9);check(model.visibleFolderRows.size()==4,"navigate expands ancestors");
        state.selectItem(SelectionType.NETWORKS,7,false);state.selectedFolderId=9;
        model.update(List.of(network(7,"Renamed",3),network(8,"beta",0)),folders);
        check(state.selectedNetworkIds.equals(Set.of(7)),"rename/move with new Java record preserves identity");
        check(model.getFolderPath(9).equals("alpha/Nested"),"folder path");
        model.update(List.of(network(8,"beta",0)),List.of(folder(3,"alpha",0),folder(7,"Beta",0)));
        check(state.selectedFolderId==3,"deleted current folder falls back to surviving ancestor");
        check(state.selectedNetworkIds.isEmpty()&&state.selectionType==SelectionType.NONE,"deleted selection never shifts to neighbor");
        state.dragId=8;state.dragType=DragType.NETWORK;state.dragging=true;
        model.update(List.of(network(8,"changed",0)),List.of());
        check(state.dragType==DragType.NONE&&state.dragId==-1&&!state.dragging,"snapshot cancels stale gesture");
        check(state.selectedFolderId==0,"deleted ancestor falls back to root");
    }
    private static void invalidTrees() {
        var state=new ManagerState();var model=new ManagerModel(state);
        model.update(List.of(),List.of(folder(1,"cycle1",2),folder(2,"cycle2",1),folder(3,"orphan",99),folder(4,"self",4)));
        check(model.visibleFolderRows.size()==5,"cycle/self/orphan displayed once in safe root fallback");
        check(!model.warning.isEmpty(),"malformed tree has explicit warning");
        check(model.folders.get(0).parentId()==2,"fallback does not rewrite server records");
        model.expandPathToFolder(1);check(model.getFolderPath(1).length()<50,"cyclic navigation and path terminate");
        model.update(List.of(),List.of(folder(0,"badroot",0),folder(1,"A",0),folder(1,"duplicate",0)));
        check(model.visibleFolderRows.size()==2&&model.folder(0).getName().equals("/"),"duplicate/root IDs cannot create recursive nodes");
        var deep=new ArrayList<NetworkListS2CPacket.FolderEntry>();
        for(int i=1;i<=1023;i++)deep.add(folder(i,"F"+i,i-1));
        model.update(List.of(),deep);
        check(model.visibleFolderRows.size()==1024,"maximum-depth tree flatten is iterative");
        check(model.getFolderPath(1023).endsWith("F1023"),"deep path terminates");
    }
    private static void brokenSelection() {
        var state=new ManagerState();var model=new ManagerModel(state);model.update(List.of(),List.of());
        var a=broken(1,1);var b=broken(1,2);var c=broken(2,1);
        model.updateBroken(List.of(a,b,c));state.selectedBrokenKeys.add(ManagerModel.key(a));
        model.selectAllBroken();check(state.selectedBrokenKeys.size()==2,"first Select All expands selected network");
        model.selectAllBroken();check(state.selectedBrokenKeys.size()==3,"second Select All expands whole list");
        check(model.selectedBrokenNetworks().equals(Set.of(1,2)),"broken composite keys retain different networks with same element ID");
        model.updateBroken(List.of(b,c));check(state.selectedBrokenKeys.size()==2,"repair removes only repaired key");
        check(model.selectedBrokenNetworks().equals(Set.of(1,2)),"damage references need not arrive with network list");
        model.updateBroken(List.of());check(state.selectedBrokenKeys.isEmpty(),"empty confirmed damage clears selection");
    }
    private static void controller() {
        var state=new ManagerState();var model=new ManagerModel(state);
        var folders=List.of(folder(1,"F",0));model.update(List.of(network(1,"A",0),network(2,"B",0)),folders);
        var sent=new ArrayList<String>();var clock=new long[]{0};var valid=new boolean[]{true};var refreshes=new int[]{0};
        var actions=new ManagerActions(model,new ManagerActions.Transport() {
            public void single(NetworkActionC2SPacket.Action a,int id,String v){sent.add(a+":"+id+":"+v);}
            public void bulk(NetworkBulkActionC2SPacket.BulkAction a,Set<Integer> ids,int target){sent.add(a+":"+ids+":"+target);}
            public void highlight(Set<Integer> ids){sent.add("highlight:"+ids);}
            public void refresh(){refreshes[0]++;}
        },()->clock[0],()->valid[0]);
        for(int i=0;i<39;i++)actions.tick();check(refreshes[0]==0,"refresh is bounded, not per-frame dispatch");
        actions.tick();check(refreshes[0]==1,"open session requests one periodic snapshot");
        var edit=actions.edit(RENAME_NETWORK,1);
        state.selectItem(SelectionType.NETWORKS,2,false);
        check(edit.save("new"),"captured edit sends");check(!edit.save("twice"),"one dialog save sends once");
        check(sent.get(0).equals("RENAME_NETWORK:1:new"),"edit does not retarget changed selection");
        var cancelled=actions.edit(RENAME_NETWORK,2);cancelled.cancel();
        check(!cancelled.save("cancelled")&&sent.size()==1,"cancel sends nothing");
        var missing=actions.edit(RENAME_NETWORK,1);model.update(List.of(network(2,"B",0)),folders);
        check(!missing.save("deleted")&&sent.size()==1,"deleted target never maps to adjacent row");
        var create=actions.edit(CREATE_FOLDER,1);state.selectedFolderId=0;
        check(create.save("Child")&&sent.get(1).equals("CREATE_FOLDER:1:Child"),"create captures original parent");
        var orphan=actions.edit(RENAME_FOLDER,1);model.update(model.entries,List.of());
        check(!orphan.save("gone"),"deleted folder edit is rejected locally");
        check(actions.bulk(DECOMPILE_NETWORKS,List.of(2),0),"bulk sends existing target");
        check(!actions.bulk(DECOMPILE_NETWORKS,List.of(2),0),"duplicate pending gesture suppressed");
        check(model.network(2)!=null,"no optimistic deletion before server result");
        clock[0]=2_000_000_001L;
        check(actions.bulk(DECOMPILE_NETWORKS,List.of(2),0),"missing/denied response cannot block retries forever");
        actions.refreshed();check(actions.bulk(DECOMPILE_NETWORKS,List.of(2),0),"refresh releases duplicate guard");
        check(!actions.bulk(MOVE_NETWORKS,List.of(2),99),"missing destination rejects without retargeting");
        check(!actions.bulk(DECOMPILE_NETWORKS,List.of(2,9),0),"mixed invalid bulk is rejected atomically");
        check(actions.highlight(List.of(2)),"highlight uses controller transport");
        var late=actions.edit(RENAME_NETWORK,2);int before=sent.size();valid[0]=false;
        check(!late.save("old world")&&sent.size()==before,"world/connection change prevents old dialog action");
        valid[0]=true;actions.close();actions.refreshed();
        check(!actions.highlight(List.of(2))&&sent.size()==before,"late refresh cannot revive closed controller");
        for(int i=0;i<80;i++)actions.tick();check(refreshes[0]==1,"closed session stops refresh requests");
    }
    private static void geometry() {
        var layout=new ManagerLayout();layout.resize(854,480);
        for(var rows:List.of(layout.folders(),layout.networks(),layout.search(),layout.broken())) {
            check(rows.index(rows.left(),92,0,100)==0,"first rendered row clickable");
            check(rows.index(rows.left(),91,0,100)==-1,"header not clickable as a row");
            int end=92+rows.count()*rows.rowHeight();
            check(rows.index(rows.left(),end,0,100)==-1,"unrendered trailing area not clickable");
            check(rows.index(rows.left(),92,4,100)==4,"scroll offset matches hit row");
            check(rows.clamp(1000,100)==100-rows.count(),"scroll clamps to last visible page");
        }
        layout.resize(320,110);check(layout.folders().count()==0&&layout.networks().count()==0,"tiny viewport has no phantom full row");
        check(layout.folders().index(10,92,0,10)==-1,"tiny viewport cannot hit invisible row");
    }
    private static void scale() {
        var state=new ManagerState();var model=new ManagerModel(state);var layout=new ManagerLayout();layout.resize(854,480);
        var folders=new ArrayList<NetworkListS2CPacket.FolderEntry>();var entries=new ArrayList<NetworkListS2CPacket.Entry>();
        for(int i=1;i<=256;i++)folders.add(folder(i,"Folder "+i,0));
        for(int i=1;i<=768;i++)entries.add(network(i,"Network "+i,i%257));
        long start=System.nanoTime();model.update(entries,folders);long build=System.nanoTime()-start;
        state.searchText="network";model.rebuildSearchResults();
        long trees=model.treeBuilds,rows=model.rowBuilds,search=model.searchBuilds;
        start=System.nanoTime();int observed=0;
        for(int i=0;i<10000;i++){observed+=model.searchResults.size();layout.clamp(model,state);}
        long reads=System.nanoTime()-start;
        model.update(new ArrayList<>(entries),new ArrayList<>(folders));
        check(observed==7_680_000,"large-list unchanged frame observations");
        check(model.treeBuilds==trees&&model.rowBuilds==rows&&model.searchBuilds==search,"unchanged frames and equal snapshots rebuild nothing");
        entries.set(0,network(1,"Renamed",0));model.update(entries,folders);
        check(model.searchResults.size()==767&&model.treeBuilds==trees,"rename invalidates search without rebuilding unchanged tree");
        System.out.printf(java.util.Locale.ROOT,"MANAGER_MODEL rows=1024 buildMs=%.3f unchanged10000ReadsMs=%.3f frameRebuilds=0%n",build/1e6,reads/1e6);
    }
}
