package com.example.compiledcircuits.networking;

import net.minecraft.core.BlockPos;
import java.util.*;

/** Incremental presentation of one committed revision: global rows and dimension-local markers. */
public final class DamageView {
    public record Position(String dimension, BlockPos pos) {}
    public final long batch, revision;
    private final String dimension;
    private final Iterator<DamageReplica.Network> networks;
    private Iterator<DamageProtocol.Element> elements = Collections.emptyIterator();
    private DamageReplica.Network network;
    private final TreeSet<BrokenElementListS2CPacket.Entry> sorted = new TreeSet<>(Comparator
            .comparing(BrokenElementListS2CPacket.Entry::networkName, String.CASE_INSENSITIVE_ORDER)
            .thenComparingInt(BrokenElementListS2CPacket.Entry::elementId)
            .thenComparingInt(BrokenElementListS2CPacket.Entry::networkId));
    private final List<BrokenElementListS2CPacket.Entry> list = new ArrayList<>();
    private final Set<BlockPos> positions = new HashSet<>();
    private final Set<Position> validFocus = new HashSet<>();
    private final Map<Integer, String> paths = new HashMap<>();
    private Iterator<BrokenElementListS2CPacket.Entry> flatten;
    private boolean done;

    public DamageView(DamageReplica replica, String dimension) {
        if (replica.status() != DamageReplica.Status.READY) throw new IllegalStateException("Unconfirmed damage view");
        batch = replica.completedBatch(); revision = replica.revision(); this.dimension = dimension;
        networks = replica.networks().iterator();
    }

    public boolean step() {
        if (done) return true;
        if (flatten != null) {
            if (flatten.hasNext()) { list.add(flatten.next()); return false; }
            done = true; return true;
        }
        if (elements.hasNext()) {
            var e = elements.next(); var m = network.meta();
            sorted.add(new BrokenElementListS2CPacket.Entry(m.id(), m.name(), m.folder(), e.id(), m.dimension(),
                    e.pos(), e.type(), e.expected(), e.actual(), e.detectedAt()));
            validFocus.add(new Position(m.dimension(), e.pos()));
            if (dimension.equals(m.dimension())) positions.add(e.pos());
            return false;
        }
        if (networks.hasNext()) {
            network = networks.next(); elements = network.elements().values().iterator();
            paths.put(network.meta().id(), network.meta().path()); return false;
        }
        flatten = sorted.iterator(); return false;
    }

    private void requireDone() { if (!done) throw new IllegalStateException("Partial damage view"); }
    public List<BrokenElementListS2CPacket.Entry> entries() { requireDone(); return Collections.unmodifiableList(list); }
    public Set<BlockPos> positions() { requireDone(); return Collections.unmodifiableSet(positions); }
    public Map<Integer, String> paths() { requireDone(); return Collections.unmodifiableMap(paths); }
    public boolean contains(String dimension, BlockPos pos) { requireDone(); return validFocus.contains(new Position(dimension, pos)); }
}
