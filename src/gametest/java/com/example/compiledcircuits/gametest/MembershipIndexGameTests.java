package com.example.compiledcircuits.gametest;

import com.example.compiledcircuits.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.*;

@GameTestHolder("compiledcircuits")
@PrefixGameTestTemplate(false)
public class MembershipIndexGameTests {
    private static final String OVERWORLD = "minecraft:overworld";
    private static CompiledNetwork network(int id, String dimension, BlockPos pos, CircuitElementType type) {
        var element = new CompiledCircuitElement(1, pos, type, switch (type) {
            case WIRE -> "compiledcircuits:basic_wire";
            case INPUT -> "compiledcircuits:input_endpoint";
            case OUTPUT -> "compiledcircuits:output_endpoint";
            default -> throw new IllegalArgumentException("Unsupported test role: " + type);
        });
        return new CompiledNetwork(id, "index test", 0, dimension,
                type == CircuitElementType.WIRE ? Set.of(pos) : Set.of(),
                type == CircuitElementType.INPUT ? Set.of(pos) : Set.of(),
                type == CircuitElementType.OUTPUT ? Set.of(pos) : Set.of(), List.of(element));
    }
    private static void matchesScan(GameTestHelper helper, NetworkSavedData data, String dimension, BlockPos pos) {
        var expected = new ArrayList<NetworkSavedData.ElementLocation>();
        for (var network : data.getNetworks()) {
            if (!network.getDimension().equals(dimension)) continue;
            for (var element : network.getElements()) if (element.getPos().equals(pos))
                expected.add(new NetworkSavedData.ElementLocation(network, element));
        }
        var actual = data.getMembershipOwners(dimension, pos);
        var indexed = data.findElementLocation(dimension, pos);
        helper.assertTrue(Objects.equals(indexed, expected.size() == 1 ? expected.get(0) : null), "public lookup agrees with reference scan");
        var level = helper.getLevel().getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, new net.minecraft.resources.ResourceLocation(dimension)));
        if (level != null) {
            var owner = indexed == null ? null : indexed.network();
            helper.assertTrue(data.findNetworkContaining(level, pos) == owner, "containing lookup agrees with scan");
            helper.assertTrue(data.findNetworkByInput(level, pos) == (indexed != null && indexed.element().getType() == CircuitElementType.INPUT ? owner : null), "input role filter");
            helper.assertTrue(data.findNetworkByOutput(level, pos) == (indexed != null && indexed.element().getType() == CircuitElementType.OUTPUT ? owner : null), "output role filter");
            if (indexed != null) helper.assertTrue(owner.getElementAt(pos) == indexed.element(), "local index agrees");
        }
        helper.assertTrue(actual.size() == expected.size() && actual.containsAll(expected), "index agrees with full scan");
        helper.assertTrue(Objects.equals(data.findIndexedElementLocation(dimension, pos),
                expected.size() == 1 ? expected.get(0) : null), "unique-owner query");
    }
    private static void immutable(GameTestHelper helper, Runnable mutation) {
        try { mutation.run(); throw new AssertionError("Mutable membership exposed"); }
        catch (UnsupportedOperationException expected) { helper.assertTrue(true, "immutable"); }
    }
    @GameTest(template="empty", timeoutTicks=100)
    public static void membershipLifecycle(GameTestHelper helper) {
        var data = new NetworkSavedData();
        var pos = new BlockPos(3, 60, 7);
        var mutable = new BlockPos.MutableBlockPos(3, 60, 7);
        var first = network(1, OVERWORLD, mutable, CircuitElementType.WIRE);
        data.addNetwork(first);
        mutable.set(99, 90, 99);
        helper.assertTrue(first.getWires().contains(pos), "defensive immutable position copy");
        matchesScan(helper, data, OVERWORLD, pos);
        helper.assertTrue(data.getMembershipOwners(OVERWORLD, mutable).isEmpty(), "mutable source cannot corrupt key");
        immutable(helper, () -> first.getWires().clear());
        immutable(helper, () -> first.getInputs().add(pos));
        immutable(helper, () -> first.getOutputs().add(pos));
        immutable(helper, () -> first.getElements().clear());
        immutable(helper, () -> data.getNetworks().clear());
        immutable(helper, () -> data.getMembershipOwners(OVERWORLD, pos).clear());
        first.markBroken(new BrokenCircuitElement(1, "minecraft:air", 0));
        first.setPowered(true); first.setFolderId(9); first.setName("changed");
        matchesScan(helper, data, OVERWORLD, pos);
        first.markRepaired(1);
        data.addNetwork(network(2, "minecraft:the_nether", pos, CircuitElementType.INPUT));
        matchesScan(helper, data, "minecraft:the_nether", pos);
        helper.assertTrue(new NetworkSavedData().getMembershipOwners(OVERWORLD, pos).isEmpty(), "no global cross-server index");
        data.addNetwork(network(3, OVERWORLD, pos.above(), CircuitElementType.OUTPUT));
        var conflicted = data;
        matchesScan(helper, data, OVERWORLD, pos);
        helper.assertTrue(conflicted.removeNetworks(List.of(1, 999)).isEmpty(), "invalid batch unchanged");
        matchesScan(helper, conflicted, OVERWORLD, pos);
        var replacement = network(1, OVERWORLD, pos.east(), CircuitElementType.INPUT);
        conflicted.replaceNetwork(replacement);
        matchesScan(helper, conflicted, OVERWORLD, pos);
        matchesScan(helper, conflicted, OVERWORLD, pos.east());
        helper.assertTrue(conflicted.findIndexedElementLocation(OVERWORLD, pos) == null, "replacement releases old claim");
        var loaded = NetworkSavedData.load(conflicted.save(new CompoundTag()));
        for (var location : List.of(pos, pos.east(), pos.above())) matchesScan(helper, loaded, OVERWORLD, location);
        matchesScan(helper, loaded, "minecraft:the_nether", pos);
        helper.assertTrue(loaded.removeNetwork(3), "single removal");
        matchesScan(helper, loaded, OVERWORLD, pos);
        helper.assertTrue(loaded.removeNetworks(List.of(1, 2, 1)).size() == 2, "deduplicated bulk removal");
        matchesScan(helper, loaded, OVERWORLD, pos.east());
        matchesScan(helper, loaded, "minecraft:the_nether", pos);
        // Legacy role sets migrate before index construction, without world reads.
        CompoundTag legacy = network(7, OVERWORLD, pos, CircuitElementType.WIRE).save();
        legacy.remove("elements");
        var list = new net.minecraft.nbt.ListTag(); list.add(legacy);
        var saved = new CompoundTag(); saved.put("networks", list);
        var migrated = NetworkSavedData.load(saved);
        matchesScan(helper, migrated, OVERWORLD, pos);
        helper.assertTrue(migrated.findIndexedElementLocation(OVERWORLD, pos) != null, "legacy indexed");
        helper.succeed();
    }

    private static void unchangedRejection(GameTestHelper helper, NetworkSavedData data, Runnable action) {
        CompoundTag before = data.save(new CompoundTag());
        data.setDirty(false);
        com.example.compiledcircuits.networking.CompiledElementSync.clear();
        rejects(action);
        helper.assertTrue(before.equals(data.save(new CompoundTag())) && !data.isDirty(), "rejection preserves saved state and ID");
        try {
            var field = com.example.compiledcircuits.networking.CompiledElementSync.class.getDeclaredField("dirty");
            field.setAccessible(true);
            helper.assertTrue(((Set<?>) field.get(null)).isEmpty(), "rejection sends no sync invalidation");
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        for (var n : data.getNetworks()) for (var e : n.getElements()) matchesScan(helper, data, n.getDimension(), e.getPos());
    }

    @GameTest(template="empty", timeoutTicks=100)
    public static void atomicAdmission(GameTestHelper helper) {
        BlockPos pos = new BlockPos(7, 70, 2);
        var roles = List.of(CircuitElementType.WIRE, CircuitElementType.INPUT, CircuitElementType.OUTPUT);
        for (var a : roles) for (var b : roles) {
            var data = new NetworkSavedData();
            var first = network(1, OVERWORLD, pos, a);
            data.addNetwork(first);
            var second = network(2, OVERWORLD, pos, b);
            unchangedRejection(helper, data, () -> data.addNetwork(second));
            helper.assertTrue(data.getNextNetworkId() == 2 && data.getNetwork(1) == first, "failed add consumes no ID");
            var empty = new NetworkSavedData();
            unchangedRejection(helper, empty, () -> empty.addNetworks(List.of(second, first)));
            helper.assertTrue(empty.getMembershipOwners(OVERWORLD, pos).isEmpty(), "failed batch adds no claims");
            try { data.addNetwork(second); throw new AssertionError("expected overlap"); }
            catch (NetworkSavedData.AdmissionException ex) {
                helper.assertTrue(ex.getMessage().contains("#1") && ex.getMessage().contains(OVERWORLD)
                        && ex.getMessage().contains(pos.toShortString()), "conflict identifies owner, dimension and position");
            }
        }
        var data = new NetworkSavedData();
        var first = network(1, OVERWORLD, pos, CircuitElementType.WIRE);
        data.addNetworks(List.of(first, network(2, OVERWORLD, pos.east(), CircuitElementType.INPUT)));
        unchangedRejection(helper, data, () -> data.addNetwork(network(1, OVERWORLD, pos.above(), CircuitElementType.OUTPUT)));
        unchangedRejection(helper, data, () -> data.replaceNetwork(network(1, OVERWORLD, pos.east(), CircuitElementType.OUTPUT)));
        unchangedRejection(helper, data, () -> data.replaceNetwork(network(99, OVERWORLD, pos.above(), CircuitElementType.OUTPUT)));
        unchangedRejection(helper, data, () -> data.addNetworks(List.of(
                network(3, OVERWORLD, pos.above(), CircuitElementType.WIRE),
                network(3, OVERWORLD, pos.below(), CircuitElementType.WIRE))));
        helper.assertTrue(data.getNetwork(1) == first, "failed replacement retains original instance");
        data.replaceNetwork(network(1, OVERWORLD, pos, CircuitElementType.OUTPUT));
        helper.assertTrue(data.getMembershipOwners(OVERWORLD, pos).get(0).element().getType() == CircuitElementType.OUTPUT, "replacement may reuse own position");
        data.replaceNetwork(network(1, "minecraft:the_nether", pos, CircuitElementType.OUTPUT));
        helper.assertTrue(data.getMembershipOwners(OVERWORLD, pos).isEmpty(), "replacement releases old dimension");
        matchesScan(helper, data, "minecraft:the_nether", pos);
        data.addNetwork(network(3, OVERWORLD, pos, CircuitElementType.INPUT));
        helper.assertTrue(data.getNextNetworkId() == 4 && data.getNextNetworkId() == 4, "ID preview does not allocate");
        CompoundTag staleCounter = data.save(new CompoundTag()); staleCounter.putInt("nextNetworkId", 1);
        var loaded = NetworkSavedData.load(staleCounter);
        helper.assertTrue(loaded.getNextNetworkId() == 4, "load repairs stale ID counter");
        data.addNetwork(network(Integer.MAX_VALUE, OVERWORLD, pos.above(), CircuitElementType.WIRE));
        rejects(data::getNextNetworkId);
        data.removeNetwork(Integer.MAX_VALUE);
        rejects(NetworkSavedData.load(data.save(new CompoundTag()))::getNextNetworkId);
        helper.succeed();
    }

    @GameTest(template="empty", timeoutTicks=100)
    public static void compilationAdmission(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var server = level.getServer();
        var data = NetworkSavedData.get(server);
        var player = new net.minecraftforge.common.util.FakePlayer(level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "membership"));
        var selection = NetworkSelectionData.get(player);
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var end = pos.east();
        var oldStart = level.getBlockState(pos);
        var oldEnd = level.getBlockState(end);
        int id = data.getNextNetworkId();
        try {
            level.setBlock(pos, com.example.compiledcircuits.registry.ModBlocks.INPUT_ENDPOINT.get().defaultBlockState(), 3);
            level.setBlock(end, com.example.compiledcircuits.registry.ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState(), 3);
            NetworkSelectionData.set(player, pos);
            // Stored wire reservation, physically replaced with an input: the old cross-role bug.
            var owner = network(id, level.dimension().location().toString(), pos, CircuitElementType.WIRE);
            data.addNetwork(owner);
            var before = data.save(new CompoundTag());
            // The name dialog no longer performs an extra scan; admission checks the submitted request.
            helper.assertTrue(server.getCommands().getDispatcher().execute("circuit compile", player.createCommandSourceStack()) == 0, "command rejects cross-role conflict");
            helper.assertTrue(!NetworkCompiler.compileSelected(player, "conflicting GUI"), "GUI admission rejects cross-role conflict");
            helper.assertTrue(before.equals(data.save(new CompoundTag())) && data.getNetwork(id) == owner, "both entry points preserve data and counter");
            data.removeNetwork(id);
            id = data.getNextNetworkId();
            helper.assertTrue(NetworkCompiler.compileSelected(player, "GUI success"), "GUI valid admission succeeds");
            helper.assertTrue(data.getNetwork(id) != null && data.getNetwork(id).getElements().size() == 2, "GUI commits complete membership");
        } finally {
            data.removeNetwork(id);
            level.setBlock(pos, oldStart, 3);
            level.setBlock(end, oldEnd, 3);
            if (selection == null) NetworkSelectionData.clear(player); else NetworkSelectionData.set(player, selection);
        }
        helper.succeed();
    }

    @GameTest(template="empty", timeoutTicks=100)
    public static void savedConflictIsolation(GameTestHelper helper) throws Exception {
        var pos = new BlockPos(8, 70, 4);
        var roles = List.of(CircuitElementType.WIRE, CircuitElementType.INPUT, CircuitElementType.OUTPUT);
        for (var a : roles) for (var b : roles) {
            var first = network(1, OVERWORLD, pos, a); first.setPowered(true);
            var second = network(2, OVERWORLD, pos, b); second.setPowered(true);
            var records = new net.minecraft.nbt.ListTag(); records.add(first.save()); records.add(second.save());
            records.add(network(3, OVERWORLD, pos.above(), CircuitElementType.WIRE).save());
            var raw = new CompoundTag(); raw.put("networks", records);
            var data = NetworkSavedData.load(raw);
            helper.assertTrue(data.getNetworks().size() == 1 && data.getNetwork(3) != null, "all overlapping owners isolated, healthy retained");
            helper.assertTrue(data.getMembershipOwners(OVERWORLD, pos).isEmpty(), "no arbitrary active owner");
            helper.assertTrue(data.getBlockingRecords(OVERWORLD, pos).size() == 2 && !data.hasUnknownMembershipReservations(), "known reservations retain both record claims");
            helper.assertTrue(data.getInvalidMembershipRecords().stream().anyMatch(r -> r.getCompound("raw").equals(first.save())), "powered raw evidence intact");
            var reload = NetworkSavedData.load(data.save(new CompoundTag()));
            helper.assertTrue(reload.getInvalidMembershipRecords().equals(data.getInvalidMembershipRecords()), "isolation survives reload without activation");
            unchangedRejection(helper, data, () -> data.addNetwork(network(4, OVERWORLD, pos, CircuitElementType.INPUT)));
            data.addNetwork(network(4, "minecraft:the_nether", pos, CircuitElementType.INPUT));
            var snapshot = com.example.compiledcircuits.networking.CompiledElementSync.buildSnapshot(data, OVERWORLD, 10);
            helper.assertTrue(snapshot.size() == 1 && snapshot.get(0).entries().size() == 1
                    && snapshot.get(0).entries().get(0).networkId() == 3 && snapshot.get(0).blocked().equals(List.of(pos)), "healthy snapshot and separate deduplicated reservation");
            String recordId = data.getInvalidMembershipRecords().get(0).getString("recordId");
            helper.assertTrue(!data.removeInvalidMembershipRecord("missing"), "unknown isolated ID rejected");
            helper.assertTrue(data.removeInvalidMembershipRecord(recordId), "explicit removal");
            helper.assertTrue(data.getBlockingRecords(OVERWORLD, pos).size() == 1 && data.getNetwork(1) == null && data.getNetwork(2) == null, "remaining claimant still blocked, no auto-reactivation");
            data.removeInvalidMembershipRecord(data.getInvalidMembershipRecords().get(0).getString("recordId"));
            helper.assertTrue(data.getBlockingRecords(OVERWORLD, pos).isEmpty(), "last removal releases reservation");
            data.addNetwork(network(5, OVERWORLD, pos, CircuitElementType.OUTPUT));
        }
        // A-B-C overlap chain: even the end records must be isolated before any insertion.
        var a = network(10, OVERWORLD, pos, CircuitElementType.WIRE);
        var c = network(12, OVERWORLD, pos.east(), CircuitElementType.WIRE);
        var b = new CompiledNetwork(11, "bridge", 0, OVERWORLD, Set.of(pos, pos.east()), Set.of(), Set.of());
        var list = new net.minecraft.nbt.ListTag(); list.add(c.save()); list.add(a.save()); list.add(b.save());
        var root = new CompoundTag(); root.put("networks", list);
        var chain = NetworkSavedData.load(root);
        helper.assertTrue(chain.getNetworks().isEmpty() && chain.getInvalidMembershipRecords().size() == 3, "full overlap chain isolated regardless of order");
        // Unknown scope retains raw evidence and blocks admissions away from known positions.
        root = new CompoundTag(); root.putString("networks", "corrupted list");
        var unknown = NetworkSavedData.load(root);
        helper.assertTrue(unknown.hasUnknownMembershipReservations(), "unknown scope fails closed");
        helper.assertTrue(unknown.getInvalidMembershipRecords().get(0).getCompound("raw").getString("unreadableNetworks").equals("corrupted list"), "unreadable list retained");
        try { unknown.addNetwork(network(1, OVERWORLD, pos, CircuitElementType.WIRE)); throw new AssertionError("unknown scope admitted"); }
        catch (IllegalStateException expected) { }
        unknown.removeInvalidMembershipRecord(unknown.getInvalidMembershipRecords().get(0).getString("recordId"));
        unknown.addNetwork(network(1, OVERWORLD, pos, CircuitElementType.WIRE));
        var badIsolation = new CompoundTag(); badIsolation.putString("invalidMembershipRecords", "unreadable isolation");
        var preserved = NetworkSavedData.load(badIsolation);
        helper.assertTrue(preserved.hasUnknownMembershipReservations()
                && preserved.getInvalidMembershipRecords().get(0).getCompound("raw").getString("unreadableIsolatedRecords").equals("unreadable isolation"), "unreadable quarantine list preserved");
        // Duplicate network IDs have separate administrative identities even at disjoint positions.
        var duplicates = new net.minecraft.nbt.ListTag();
        duplicates.add(network(40, OVERWORLD, pos, CircuitElementType.WIRE).save());
        duplicates.add(network(40, OVERWORLD, pos.east(), CircuitElementType.WIRE).save());
        var duplicateRoot = new CompoundTag(); duplicateRoot.put("networks", duplicates);
        var duplicateData = NetworkSavedData.load(duplicateRoot);
        var isolatedRecords = duplicateData.getInvalidMembershipRecords();
        helper.assertTrue(!isolatedRecords.get(0).getString("recordId").equals(isolatedRecords.get(1).getString("recordId")), "duplicate network IDs use distinct record IDs");
        duplicateData.removeInvalidMembershipRecord(isolatedRecords.get(0).getString("recordId"));
        helper.assertTrue(duplicateData.getInvalidMembershipRecords().size() == 1
                && duplicateData.getBlockedPositions(OVERWORLD).equals(Set.of(pos.east())), "record removal releases only its own positions");
        // A valid-looking record overlapping an already isolated record must also be blocked.
        var persistedConflict = duplicateData.save(new CompoundTag());
        persistedConflict.getList("networks",10).add(network(41, OVERWORLD, pos.east(), CircuitElementType.INPUT).save());
        helper.assertTrue(NetworkSavedData.load(persistedConflict).getNetwork(41) == null, "existing isolated reservations block load-time owners");
        var persistedIdConflict = duplicateData.save(new CompoundTag());
        persistedIdConflict.getList("networks",10).add(network(40, OVERWORLD, pos.above(), CircuitElementType.WIRE).save());
        var sameId = NetworkSavedData.load(persistedIdConflict);
        helper.assertTrue(sameId.getNetwork(40) == null && sameId.getInvalidMembershipRecords().size() == 2,
                "isolated ID also blocks a disjoint loaded record with that ID");
        // Multipart reserved positions share packet budgets without entering active membership.
        var many = new java.util.HashSet<BlockPos>();
        for (int i = 0; i < 5000; i++) many.add(new BlockPos(i, 200, 0));
        var manyNetwork = new CompiledNetwork(50, "many", 0, OVERWORLD, many, Set.of(), Set.of());
        var manyList = new net.minecraft.nbt.ListTag(); manyList.add(manyNetwork.save()); manyList.add(manyNetwork.save());
        var manyRoot = new CompoundTag(); manyRoot.put("networks", manyList);
        var parts = com.example.compiledcircuits.networking.CompiledElementSync.buildSnapshot(NetworkSavedData.load(manyRoot), OVERWORLD, 11);
        helper.assertTrue(parts.size() == 2 && parts.stream().allMatch(part -> part.entries().isEmpty())
                && parts.stream().mapToInt(part -> part.blocked().size()).sum() == 5000, "reserved-only multipart snapshot");
        helper.succeed();
    }

    @GameTest(template="empty", timeoutTicks=100)
    public static void isolatedRuntime(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var storage = level.getServer().overworld().getDataStorage();
        var original = NetworkSavedData.get(level.getServer());
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var healthyPos = pos.offset(3, 0, 0);
        var old = level.getBlockState(pos); var oldHealthy = level.getBlockState(healthyPos);
        var output = com.example.compiledcircuits.registry.ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState();
        var first = network(1001, OVERWORLD, pos, CircuitElementType.OUTPUT); first.setPowered(true);
        var other = network(1002, OVERWORLD, pos, CircuitElementType.WIRE);
        var healthy = network(1003, OVERWORLD, healthyPos, CircuitElementType.OUTPUT); healthy.setPowered(true);
        var list = new net.minecraft.nbt.ListTag(); list.add(first.save()); list.add(other.save()); list.add(healthy.save());
        var raw = new CompoundTag(); raw.put("networks", list);
        var isolated = NetworkSavedData.load(raw);
        try {
            storage.set("compiledcircuits_networks", isolated);
            level.setBlock(pos, output, 3); level.setBlock(healthyPos, output, 3);
            helper.assertTrue(output.getSignal(level, pos, net.minecraft.core.Direction.UP) == 0, "isolated powered output is LOW");
            helper.assertTrue(output.getSignal(level, healthyPos, net.minecraft.core.Direction.UP) == 15, "healthy powered output continues");
            var before = isolated.getInvalidMembershipRecords();
            NetworkIntegrityManager.checkPosition(level, pos);
            helper.assertTrue(isolated.getInvalidMembershipRecords().equals(before) && isolated.getNetwork(1001) == null, "repair cannot activate isolated record");
            var dispatcher = level.getServer().getCommands().getDispatcher();
            var admin = level.getServer().createCommandSourceStack();
            helper.assertTrue(dispatcher.execute("circuit conflicts list", admin) == 2, "administrator can inspect isolated records");
            var fake = new net.minecraftforge.common.util.FakePlayer(level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "membership")).createCommandSourceStack().withPermission(0);
            try { dispatcher.execute("circuit conflicts remove " + before.get(0).getString("recordId"), fake); throw new AssertionError("non-admin removal"); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { }
            helper.assertTrue(dispatcher.execute("circuit conflicts remove " + before.get(0).getString("recordId"), admin) == 1, "administrator removes one explicit record");
            helper.assertTrue(isolated.getBlockingRecords(OVERWORLD, pos).size() == 1, "command preserves other reservation");
        } finally {
            storage.set("compiledcircuits_networks", original);
            level.setBlock(pos, old, 3); level.setBlock(healthyPos, oldHealthy, 3);
        }
        helper.succeed();
    }

    private static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("Expected invalid membership rejection"); }
        catch (IllegalArgumentException expected) { }
    }
    @GameTest(template="empty", timeoutTicks=100)
    public static void authoritativeMembership(GameTestHelper helper) {
        BlockPos pos = new BlockPos(7, 70, 2);
        var wire = new CompiledCircuitElement(1, pos, CircuitElementType.WIRE, "compiledcircuits:basic_wire");
        var input = new CompiledCircuitElement(2, pos.east(), CircuitElementType.INPUT, "compiledcircuits:input_endpoint");
        var output = new CompiledCircuitElement(3, pos.west(), CircuitElementType.OUTPUT, "compiledcircuits:output_endpoint");
        var elements = new ArrayList<>(List.of(wire, input, output));
        var valid = new CompiledNetwork(10, "derived", 0, OVERWORLD, elements);
        elements.clear();
        helper.assertTrue(valid.getElements().size() == 3, "defensive element collection");
        helper.assertTrue(valid.getWires().equals(Set.of(pos)) && valid.getInputs().equals(Set.of(pos.east()))
                && valid.getOutputs().equals(Set.of(pos.west())), "roles derived from elements");
        rejects(() -> new CompiledNetwork(10, "bad", 0, OVERWORLD, Set.of(), Set.of(), Set.of(), List.of(wire)));
        rejects(() -> new CompiledNetwork(10, "bad", 0, OVERWORLD, List.of(wire, wire)));
        rejects(() -> new CompiledNetwork(10, "bad", 0, OVERWORLD, List.of(wire,
                new CompiledCircuitElement(1, pos.above(), CircuitElementType.WIRE, "compiledcircuits:basic_wire"))));
        rejects(() -> new CompiledNetwork(10, "bad", 0, OVERWORLD, List.of(
                new CompiledCircuitElement(0, pos, CircuitElementType.WIRE, "compiledcircuits:basic_wire"))));
        rejects(() -> new CompiledNetwork(10, "bad", 0, OVERWORLD, List.of(
                new CompiledCircuitElement(1, pos, CircuitElementType.INPUT, "compiledcircuits:basic_wire"))));
        for (CircuitElementType a : List.of(CircuitElementType.WIRE, CircuitElementType.INPUT, CircuitElementType.OUTPUT)) {
            for (CircuitElementType b : List.of(CircuitElementType.WIRE, CircuitElementType.INPUT, CircuitElementType.OUTPUT)) {
                var first = network(1, OVERWORLD, pos, a).getElement(1);
                var secondTemplate = network(2, OVERWORLD, pos, b).getElement(1);
                var second = new CompiledCircuitElement(2, pos, b, secondTemplate.getBlockId());
                rejects(() -> new CompiledNetwork(10, "overlap", 0, OVERWORLD, List.of(first, second)));
            }
        }
        var legacy = new CompiledNetwork(12, "legacy API", 0, OVERWORLD, Set.of(pos), Set.of(), Set.of());
        helper.assertTrue(legacy.getElements().size() == 1, "legacy constructor establishes elements");
        helper.assertTrue(CompiledNetwork.load(valid.save()).getInputs().equals(valid.getInputs()), "derived views survive load");

        var originals = new ArrayList<CompoundTag>();
        CompoundTag mismatch = valid.save(); mismatch.put("inputs", new net.minecraft.nbt.ListTag()); originals.add(mismatch);
        CompoundTag duplicateElement = valid.save();
        var e = duplicateElement.getList("elements", net.minecraft.nbt.Tag.TAG_COMPOUND); e.add(e.getCompound(0).copy()); originals.add(duplicateElement);
        CompoundTag duplicatePosition = valid.save();
        var positions = duplicatePosition.getList("wires", net.minecraft.nbt.Tag.TAG_COMPOUND); positions.add(positions.getCompound(0).copy()); originals.add(duplicatePosition);
        CompoundTag badList = valid.save(); badList.putString("elements", "not a list"); originals.add(badList);
        CompoundTag unknownRole = valid.save(); unknownRole.getList("elements", 10).getCompound(0).putString("type", "NOT_A_ROLE"); originals.add(unknownRole);
        CompoundTag incomplete = valid.save(); incomplete.getList("elements", 10).getCompound(0).remove("pos"); originals.add(incomplete);
        for (CompoundTag raw : originals) {
            var list = new net.minecraft.nbt.ListTag(); list.add(raw.copy());
            list.add(network(20, OVERWORLD, pos.above(), CircuitElementType.WIRE).save());
            var root = new CompoundTag(); root.put("networks", list);
            var data = NetworkSavedData.load(root);
            helper.assertTrue(data.getNetwork(10) == null && data.getNetwork(20) != null, "bad record excluded, healthy retained");
            helper.assertTrue(data.hasInvalidMembershipRecords(), "invalid saved record preserved");
            helper.assertTrue(data.getInvalidMembershipRecords().get(0).getCompound("raw").equals(raw), "original raw NBT intact");
            var stored = data.getInvalidMembershipRecords().get(0);
            data.getInvalidMembershipRecords().get(0).getCompound("raw").remove("elements");
            helper.assertTrue(data.getInvalidMembershipRecords().get(0).equals(stored), "raw getter defensive");
            var reloaded = NetworkSavedData.load(data.save(new CompoundTag()));
            helper.assertTrue(reloaded.getInvalidMembershipRecords().equals(data.getInvalidMembershipRecords()), "isolation persists without retry");
            matchesScan(helper, reloaded, OVERWORLD, pos.above());
            try { data.addNetwork(valid); throw new AssertionError("Unresolved reservations must block add"); }
            catch (IllegalStateException | IllegalArgumentException expected) { }
            helper.assertTrue(data.getNetwork(20) != null && data.getNetwork(10) == null, "rejected add unchanged");
        }
        var duplicates = new net.minecraft.nbt.ListTag(); duplicates.add(valid.save()); duplicates.add(valid.save());
        var root = new CompoundTag(); root.put("networks", duplicates);
        var data = NetworkSavedData.load(root);
        helper.assertTrue(data.getNetworks().isEmpty() && data.getInvalidMembershipRecords().size() == 2, "duplicate network IDs not overwritten");
        helper.succeed();
    }
}

