package com.example.compiledcircuits.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Physical placements plus ID reservations derived from active compiled networks. */
public final class WireCatalog extends SavedData {
    private record Location(String dimension, long position) {}
    private final Map<Location, String> placements = new HashMap<>();
    private final Map<String, Integer> counts = new HashMap<>();
    private final Map<String, Integer> colors = new TreeMap<>(WireIds::compare);
    private Set<String> compiledIds = Set.of();
    private NetworkSavedData compiledSource;
    private long compiledRevision = Long.MIN_VALUE;
    private long revision;
    public static WireCatalog get(MinecraftServer server) {
        var catalog = server.overworld().getDataStorage().computeIfAbsent(WireCatalog::load, WireCatalog::new, "compiledcircuits_wire_ids");
        catalog.syncCompiled(NetworkSavedData.get(server));
        return catalog;
    }
    /** Rebuild only when membership changes, using per-network summaries instead of world reads.
     * Damaged and unloaded members still reserve their ID; retired networks do not.
     */
    void syncCompiled(NetworkSavedData networks) {
        long membership = networks.highlightMembershipRevision();
        if (compiledSource == networks && compiledRevision == membership) return;
        var retained = new HashSet<String>();
        boolean changed = false;
        for (var network : networks.getNetworks()) for (var entry : network.wirePalette().entrySet()) {
            retained.add(entry.getKey());
            changed |= colors.putIfAbsent(entry.getKey(), entry.getValue()) == null;
        }
        changed |= !compiledIds.equals(retained);
        compiledIds = Set.copyOf(retained);
        changed |= colors.keySet().removeIf(id -> !counts.containsKey(id) && !compiledIds.contains(id));
        compiledSource = networks; compiledRevision = membership;
        if (changed) changed();
    }
    public boolean isColorLocked(String id) { return id.equals("0") || compiledIds.contains(id); }
    public long revision() { return revision; }
    public Map<String, Integer> colors() { return Collections.unmodifiableMap(colors); }
    public int color(String id, int fallback) { return id.equals("0") ? WireIds.RED : colors.getOrDefault(id, fallback); }
    public int count(String id) { return counts.getOrDefault(id, 0); }
    public void recolor(String id, int color) {
        if (!colors.containsKey(id) || isColorLocked(id) || colors.get(id) == (color & 0xffffff)) return;
        colors.put(id, color & 0xffffff); changed();
    }
    private void changed() { revision++; setDirty(); }
    public void placed(ServerLevel level, BlockPos pos, String id, int color) {
        placed(level.dimension().location().toString(), pos, id, color);
    }
    public void placed(String dimension, BlockPos pos, String id, int color) {
        id = WireIds.normalize(id);
        if (id.equals("0")) throw new IllegalArgumentException("ID 0 is basic wire");
        var location = new Location(dimension, pos.asLong());
        if (id.equals(placements.get(location))) return;
        remove(dimension, pos);
        placements.put(location, id); counts.merge(id, 1, Integer::sum);
        if (colors.putIfAbsent(id, color & 0xffffff) == null) revision++;
        setDirty();
    }
    public void remove(ServerLevel level, BlockPos pos) { remove(level.dimension().location().toString(), pos); }
    public void remove(String dimension, BlockPos pos) {
        String old = placements.remove(new Location(dimension, pos.asLong()));
        if (old == null) return;
        int remaining = counts.get(old) - 1;
        if (remaining == 0) {
            counts.remove(old);
            if (!compiledIds.contains(old)) { colors.remove(old); revision++; }
        } else counts.put(old, remaining);
        setDirty();
    }
    public static WireCatalog load(CompoundTag tag) {
        var data = new WireCatalog(); var palette = tag.getCompound("colors");
        // Keep saved colors until active networks are reconciled, including broken-only IDs.
        for (String id : palette.getAllKeys()) {
            try { if (!id.equals("0") && WireIds.normalize(id).equals(id)) data.colors.put(id, palette.getInt(id) & 0xffffff); }
            catch (IllegalArgumentException ignored) { }
        }
        for (Tag entry : tag.getList("placements", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) entry;
            try { data.placed(row.getString("dimension"), BlockPos.of(row.getLong("pos")), row.getString("id"), palette.getInt(row.getString("id"))); }
            catch (IllegalArgumentException ignored) { }
        }
        data.setDirty(false); return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        var palette = new CompoundTag(); colors.forEach(palette::putInt); tag.put("colors", palette);
        var list = new ListTag(); placements.forEach((location, id) -> {
            var row = new CompoundTag(); row.putString("dimension", location.dimension); row.putLong("pos", location.position); row.putString("id", id); list.add(row);
        }); tag.put("placements", list); return tag;
    }
}
