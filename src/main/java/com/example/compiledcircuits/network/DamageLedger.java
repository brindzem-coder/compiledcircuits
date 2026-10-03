package com.example.compiledcircuits.network;

import com.example.compiledcircuits.networking.DamageProtocol;
import java.util.*;
import static com.example.compiledcircuits.networking.DamageProtocol.*;

/** Persistent published bases and bounded dirty keys. No snapshot copy in a world callback. */
public final class DamageLedger {
    public record View(CompiledNetwork owner,String name,int folder,String path,PersistentIntMap<BrokenCircuitElement> broken) {
        public Meta meta(){return new Meta(owner.getId(),name,folder,owner.getDimension(),path);}
        public Element element(BrokenCircuitElement b){
            var e=owner.getElement(b.getElementId());
            if(e==null)throw new IllegalArgumentException("Missing damage member");
            return new Element(owner.getId(),e.getId(),e.getPos(),e.getType(),e.getBlockId(),b.getActualBlockId(),b.getDetectedAt());
        }
    }
    private record Key(int id) {}
    private record Dirty(int id,boolean replace,PersistentIntMap<Key> keys) {}
    public interface Cursor { boolean done(); Change step(); }
    public record Publication(long base,long revision,boolean overflow,Cursor cursor) {}
    private final NetworkSavedData data;
    private PersistentIntMap<View> root=new PersistentIntMap<>(), published=new PersistentIntMap<>();
    private PersistentIntMap<Dirty> dirty=new PersistentIntMap<>();
    private int dirtyCount;
    private boolean changed,overflow;
    private long revision,folderGeneration,folderPass;
    private Iterator<View> folders;
    public DamageLedger(NetworkSavedData data){this.data=data;}
    public long revision(){return revision;}
    private String path(int id){
        var parts=new ArrayDeque<String>();int length=0,depth=0;
        while(id!=0 && depth++<128){var f=data.getFolder(id);if(f==null)break;parts.addFirst(f.getName());length+=f.getName().length()+1;
            if(length>PATH)return "!".repeat(PATH+1);id=f.getParentId();}
        return depth>=128?"!".repeat(PATH+1):String.join("/",parts);
    }
    private void capture(CompiledNetwork n){root=root.put(n.getId(),new View(n,n.getName(),n.getFolderId(),path(n.getFolderId()),n.damageRoot()));}
    public void add(CompiledNetwork n){n.damageLedger=this;capture(n);mark(n.getId(),0,true);}
    public void remove(CompiledNetwork n){
        var v=root.get(n.getId());if(v==null||v.owner()!=n)return;
        n.damageLedger=null;root=root.remove(n.getId());mark(n.getId(),0,true);
    }
    void element(CompiledNetwork n,int id){capture(n);mark(n.getId(),id,false);}
    void metadata(CompiledNetwork n){capture(n);mark(n.getId(),0,false);}
    public void foldersChanged(){folderGeneration++;}
    public boolean hasFolderWork(){return folders!=null || folderPass!=folderGeneration;}
    public void folderStep(){
        if(folders==null){folderPass=folderGeneration;folders=root.values().iterator();}
        if(!folders.hasNext()){folders=null;return;}
        var v=folders.next();if(data.getNetwork(v.owner.getId())==v.owner)metadata(v.owner);
    }
    private void mark(int network,int element,boolean replace){
        changed=true;if(overflow)return;
        var previous=dirty.get(network);var keys=previous==null?new PersistentIntMap<Key>():previous.keys;
        var before=published.get(network);var after=root.get(network);
        if(element>0){
            var a=before==null?null:before.broken.get(element);var b=after==null?null:after.broken.get(element);
            keys=same(a,b)?keys.remove(element):keys.put(element,new Key(element));
        }
        boolean replacing=replace || previous!=null&&previous.replace;
        boolean invisible=(before==null||before.broken.isEmpty())&&(after==null||after.broken.isEmpty());
        boolean sameMetadata=before!=null&&after!=null&&Objects.equals(before.name,after.name)&&before.folder==after.folder
                &&Objects.equals(before.path,after.path)&&before.owner.getDimension().equals(after.owner.getDimension());
        if(previous!=null)dirtyCount-=1+previous.keys.size();
        if(invisible || !replacing&&keys.isEmpty()&&sameMetadata){dirty=dirty.remove(network);changed=!dirty.isEmpty();return;}
        dirtyCount+=1+keys.size();
        if(dirtyCount>MAX_PENDING){overflow=true;dirty=new PersistentIntMap<>();dirtyCount=0;return;}
        dirty=dirty.put(network,new Dirty(network,replacing,keys));
    }
    public int pendingCount(){return dirtyCount;}
    public Publication publish(){
        if(!changed)return null;
        var before=published;var after=root;var changes=dirty;boolean lost=overflow;long base=revision++;
        published=root;dirty=new PersistentIntMap<>();dirtyCount=0;changed=overflow=false;
        return new Publication(base,revision,lost,lost?null:delta(before,after,changes));
    }
    public Cursor snapshot(){return snapshot(published);}
    private static Cursor snapshot(PersistentIntMap<View> root){
        return new Cursor(){
            final Iterator<View> networks=root.values().iterator();Iterator<BrokenCircuitElement> elements=Collections.emptyIterator();View current;
            public boolean done(){return !networks.hasNext()&&!elements.hasNext();}
            public Change step(){
                if(elements.hasNext())return Change.upsert(current.element(elements.next()));
                if(!networks.hasNext())return null;current=networks.next();elements=current.broken.values().iterator();
                return elements.hasNext()?Change.meta(current.meta()):null;
            }
        };
    }
    private static boolean same(BrokenCircuitElement a,BrokenCircuitElement b){return a==b || a!=null&&b!=null&&a.getDetectedAt()==b.getDetectedAt()&&a.getActualBlockId().equals(b.getActualBlockId());}
    private static Cursor delta(PersistentIntMap<View> before,PersistentIntMap<View> after,PersistentIntMap<Dirty> dirty){
        return new Cursor(){
            final Iterator<Dirty> networks=dirty.values().iterator();Iterator<Key> keys=Collections.emptyIterator();
            Iterator<BrokenCircuitElement> all=Collections.emptyIterator();View old,current;boolean meta,clear;
            public boolean done(){return !meta&&!clear&&!all.hasNext()&&!keys.hasNext()&&!networks.hasNext();}
            public Change step(){
                if(clear){clear=false;return Change.removeNetwork(current.owner.getId());}
                if(meta){meta=false;return Change.meta(current.meta());}
                if(all.hasNext())return Change.upsert(current.element(all.next()));
                if(keys.hasNext()){
                    int id=keys.next().id;var a=old==null?null:old.broken.get(id);var b=current.broken.get(id);
                    return same(a,b)?null:b==null?Change.remove(current.owner.getId(),id):Change.upsert(current.element(b));
                }
                if(!networks.hasNext())return null;
                var d=networks.next();old=before.get(d.id);current=after.get(d.id);
                if(current==null || current.broken.isEmpty())return old!=null&&!old.broken.isEmpty()?Change.removeNetwork(d.id):null;
                if(d.replace || old==null || old.broken.isEmpty()){
                    all=current.broken.values().iterator();meta=true;
                    return old!=null&&!old.broken.isEmpty()?Change.removeNetwork(d.id):null;
                }
                keys=d.keys.values().iterator();
                return current.meta().equals(old.meta())?null:Change.meta(current.meta());
            }
        };
    }
}
