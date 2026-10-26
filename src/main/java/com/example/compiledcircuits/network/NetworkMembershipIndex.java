package com.example.compiledcircuits.network;

import net.minecraft.core.BlockPos;
import java.util.*;

/** Derived, server-owned membership only: never reads worlds or stores NBT.
 * Saved-data admission/load validation excludes conflicts before publishing this index.
 */
final class NetworkMembershipIndex {
    private record Key(String dimension, BlockPos position) {
        Key {
            var resource = net.minecraft.resources.ResourceLocation.tryParse(Objects.requireNonNull(dimension));
            if (resource == null) throw new IllegalArgumentException("Invalid dimension: " + dimension);
            dimension = resource.toString();
            position = position.immutable();
        }
    }
    private final Map<Key, List<NetworkSavedData.ElementLocation>> claims = new HashMap<>();

    void add(CompiledNetwork network) {
        for (CompiledCircuitElement element : network.getElements()) {
            Key key = new Key(network.getDimension(), element.getPos());
            var previous = claims.getOrDefault(key, List.of());
            var updated = new ArrayList<>(previous);
            updated.add(new NetworkSavedData.ElementLocation(network, element));
            updated.sort(Comparator.comparingInt((NetworkSavedData.ElementLocation c) -> c.network().getId())
                    .thenComparingInt(c -> c.element().getId()));
            claims.put(key, List.copyOf(updated));
        }
    }

    /** Prepare only touched positions. No live claims change until commit. */
    Runnable prepareAddition(List<CompiledNetwork> candidates, CompiledNetwork replaced) {
        Map<Key, List<NetworkSavedData.ElementLocation>> updates = new HashMap<>();
        if (replaced != null) {
            for (var element : replaced.getElements()) {
                Key key = new Key(replaced.getDimension(), element.getPos());
                updates.put(key, claims.getOrDefault(key, List.of()).stream()
                        .filter(c -> c.network() != replaced && !c.network().isRetired()).toList());
            }
        }
        for (var network : candidates) {
            var elements = network.getElements().stream()
                    .sorted(Comparator.comparing(CompiledCircuitElement::getPos)).toList();
            for (var element : elements) {
                Key key = new Key(network.getDimension(), element.getPos());
                var owners = updates.getOrDefault(key, claims.getOrDefault(key, List.of())).stream().filter(c -> !c.network().isRetired()).toList();
                if (owners.stream().anyMatch(o -> Crossings.overlaps(o.element(),element))) {
                    var owner = owners.get(0).network();
                    throw new NetworkSavedData.AdmissionException("Position " + element.getPos().toShortString()
                            + " in " + network.getDimension() + " already belongs to "
                            + owner.getName() + " (#" + owner.getId() + ").");
                }
                var updated=new ArrayList<>(owners);updated.add(new NetworkSavedData.ElementLocation(network,element));
                updates.put(key,List.copyOf(updated));
            }
        }
        return () -> updates.forEach((key, owners) -> {
            if (owners.isEmpty()) claims.remove(key); else claims.put(key, owners);
        });
    }

    void remove(CompiledNetwork network) {
        for (CompiledCircuitElement element : network.getElements()) {
            Key key = new Key(network.getDimension(), element.getPos());
            var previous = claims.get(key);
            if (previous == null) continue;
            // Identity matters: never remove another network's ownership at this position.
            var remaining = previous.stream().filter(c -> c.network() != network).toList();
            if (remaining.isEmpty()) claims.remove(key); else claims.put(key, remaining);
        }
    }

    void stage(CompiledNetwork network, CompiledCircuitElement element) {
        Key key = new Key(network.getDimension(), element.getPos());
        var previous = claims.get(key);
        if (previous != null && previous.stream().anyMatch(c -> !c.network().isRetired() && Crossings.overlaps(c.element(),element))) throw new NetworkSavedData.AdmissionException("Position already claimed: " + element.getPos());
        var updated=new ArrayList<NetworkSavedData.ElementLocation>();if(previous!=null)previous.stream().filter(c->!c.network().isRetired()).forEach(updated::add);
        updated.add(new NetworkSavedData.ElementLocation(network,element));claims.put(key,List.copyOf(updated));
    }
    void unstage(CompiledNetwork network, CompiledCircuitElement element) {
        Key key = new Key(network.getDimension(), element.getPos());
        var value = claims.get(key);
        if(value!=null){var remaining=value.stream().filter(c->c.network()!=network).toList();if(remaining.isEmpty())claims.remove(key);else claims.put(key,remaining);}
    }

    List<NetworkSavedData.ElementLocation> owners(String dimension, BlockPos position) {
        var owners = claims.getOrDefault(new Key(dimension, position), List.of());
        return owners.stream().filter(o->o.network().isPublished()).toList();
    }
}
