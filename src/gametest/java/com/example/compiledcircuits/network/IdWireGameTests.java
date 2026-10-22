package com.example.compiledcircuits.network;

import com.example.compiledcircuits.block.*;
import com.example.compiledcircuits.item.IdWireItem;
import com.example.compiledcircuits.registry.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("compiledcircuits")
@PrefixGameTestTemplate(false)
public class IdWireGameTests {
    private static IdWireEntity put(GameTestHelper h, BlockPos pos, String id, int color) {
        h.getLevel().setBlock(pos, ModBlocks.ID_WIRE.get().defaultBlockState(), 3);
        var wire = (IdWireEntity) h.getLevel().getBlockEntity(pos); wire.configure(id, color); return wire;
    }
    @GameTest(template="empty", batch="id_wire")
    public static void catalogOnlyTracksPhysicalWires(GameTestHelper h) {
        var data = new WireCatalog();
        h.assertTrue(data.colors().isEmpty(), "new world has no custom IDs");
        var stack = new ItemStack(ModItems.ID_WIRE.get());
        IdWireItem.configure(stack, "999999999999999999999999", 0x123456);
        data.recolor(IdWireItem.id(stack), 0xabcdef);
        h.assertTrue(data.colors().isEmpty(), "selection and recolor do not create an unused ID");
        var pos = BlockPos.ZERO;
        data.placed("minecraft:overworld", pos, IdWireItem.id(stack), 0x123456);
        data.placed("minecraft:overworld", pos, IdWireItem.id(stack), 0x111111);
        data.placed("minecraft:the_nether", pos, IdWireItem.id(stack), 0x222222);
        h.assertTrue(data.count(IdWireItem.id(stack)) == 2, "duplicate load counts once and dimensions are independent");
        data.recolor(IdWireItem.id(stack), 0xabcdef);
        var restored = WireCatalog.load(data.save(new CompoundTag()));
        h.assertTrue(restored.color(IdWireItem.id(stack), 0) == 0xabcdef, "global color survives save/load");
        restored.remove("minecraft:overworld", pos);
        h.assertTrue(restored.colors().size() == 1, "another dimension retains the ID");
        restored.remove("minecraft:the_nether", pos);
        h.assertTrue(restored.colors().isEmpty(), "last physical wire removes ID and color");
        h.assertTrue(restored.color("0", 123) == WireIds.RED && WireIds.normalize("0000").equals("0"), "zero remains canonical and red");
        h.succeed();
    }
    @GameTest(template="empty", batch="id_wire")
    public static void isolatedSignalsAndBasicBridge(GameTestHelper h) {
        var a = new BlockPos(0, 0, 0); var b = a.east(); var input = a.north(); var output = b.south();
        Map<BlockPos,BlockState> states = new HashMap<>();
        states.put(a, ModBlocks.ID_WIRE.get().defaultBlockState()); states.put(b, ModBlocks.ID_WIRE.get().defaultBlockState());
        states.put(input, ModBlocks.INPUT_ENDPOINT.get().defaultBlockState()); states.put(output, ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState());
        var isolated = LogicProgram.compile(states, Map.of(a, "1", b, "2"));
        h.assertTrue(!isolated.evaluate(Map.of(input, true)).get(output), "different adjacent IDs carry independent signals");
        h.assertTrue(LogicProgram.compile(states, Map.of(a, "1", b, "1")).evaluate(Map.of(input, true)).get(output), "same IDs conduct");
        states.put(a.above(), ModBlocks.BASIC_WIRE.get().defaultBlockState()); states.put(b.above(), ModBlocks.BASIC_WIRE.get().defaultBlockState());
        h.assertTrue(LogicProgram.compile(states, Map.of(a, "1", b, "2")).evaluate(Map.of(input, true)).get(output), "basic wire intentionally joins both IDs");
        h.succeed();
    }
    @GameTest(template="empty", batch="id_wire")
    public static void physicalRemovalAndChunkLifecycle(GameTestHelper h) {
        var level = h.getLevel(); var a = h.absolutePos(new BlockPos(1, 2, 1)); var b = a.east();
        String id = "98234567001"; var data = WireCatalog.get(level.getServer());
        var wire = put(h, a, id, 0x123456); put(h, b, id, 0x654321);
        h.assertTrue(data.count(id) == 2, "two physical blocks counted");
        wire.onLoad(); wire.onChunkUnloaded();
        h.assertTrue(data.count(id) == 2, "reload and unload do not change membership");
        data.recolor(id, 0xabcdef);
        h.assertTrue(wire.getUpdateTag().getInt("WireColor") == 0xabcdef, "loaded wire serialization sees global color");
        level.setBlock(a, Blocks.AIR.defaultBlockState(), 3);
        h.assertTrue(data.count(id) == 1, "first break retains ID");
        level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
        h.assertTrue(!data.colors().containsKey(id), "last break removes ID"); h.succeed();
    }
    @GameTest(template="empty", batch="id_wire")
    public static void discoveryIntegrityAndPersistence(GameTestHelper h) {
        var level = h.getLevel(); var a = h.absolutePos(new BlockPos(1, 2, 1)); var b = a.east();
        put(h, a, "876123", 0xffaa00); var wire = put(h, b, "876124", 0xffaa00);
        var state = level.getBlockState(a);
        h.assertTrue(!CircuitConnections.connects(level, a, state, level.getBlockState(b), Direction.EAST), "same color does not join different IDs");
        var element = new CompiledCircuitElement(1, b, CircuitElementType.WIRE, CompiledBlockStateCodec.capture(level.getBlockState(b))).captureWire(level);
        var loaded = CompiledCircuitElement.load(element.save());
        h.assertTrue(loaded.wireId().equals("876124"), "compiled identity survives save/load");
        wire.configure("876123", 0xffaa00);
        h.assertTrue(CircuitConnections.connects(level, a, state, level.getBlockState(b), Direction.EAST), "matching identity joins");
        h.assertTrue(CompiledBlockStateMatcher.match(loaded, level, level.getBlockState(b), false) == CompiledBlockStateMatcher.Match.STATE_MISMATCH, "changing ID damages compiled wire");
        h.assertTrue(CompiledBlockStateMatcher.match(loaded, ModBlocks.NETWORK_WIRE.get().defaultBlockState(), false) == CompiledBlockStateMatcher.Match.BLOCK_MISMATCH, "generic wire cannot silently replace isolated wire");
        level.setBlock(a, Blocks.AIR.defaultBlockState(), 3); level.setBlock(b, Blocks.AIR.defaultBlockState(), 3); h.succeed();
    }
    @GameTest(template="empty", batch="id_wire")
    public static void zeroPlacesBasicAndDraftDoesNotRegister(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(1, 2, 1));
        var player = new net.minecraftforge.common.util.FakePlayer(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "id-wire-test"));
        var stack = new ItemStack(ModItems.ID_WIRE.get(), 4); player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), Direction.UP, pos, false);
        var context = new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        ModItems.ID_WIRE.get().useOn(context);
        h.assertTrue(level.getBlockState(pos).is(ModBlocks.BASIC_WIRE.get()), "default ID 0 places real basic wire");
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        IdWireItem.configure(stack, "532097", 0x12ab34);
        h.assertTrue(!WireCatalog.get(level.getServer()).colors().containsKey("532097"), "draft not registered");
        ModItems.ID_WIRE.get().useOn(context);
        h.assertTrue(level.getBlockEntity(pos) instanceof IdWireEntity wire && wire.wireId().equals("532097"), "item places configured identity");
        h.assertTrue(WireCatalog.get(level.getServer()).color("532097", 0) == 0x12ab34, "first placement records color");
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); h.succeed();
    }

    @GameTest(template="empty", batch="id_wire")
    public static void protectedRepairRestoresIdentity(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(1, 2, 1));
        var player = new net.minecraftforge.common.util.FakePlayer(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "wire-repair-test"));
        String oldId = "87332001", newId = "87332002";
        var wire = put(h, pos, oldId, 0x112233); var state = level.getBlockState(pos);
        java.util.function.Consumer<net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent> deny = event -> { if (event.getEntity() == player) event.setCanceled(true); };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST, false, net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent.class, deny);
        try {
            boolean placed = ProtectedRepairPlacement.place(level, pos, state, player, () -> true, () -> ((IdWireEntity)level.getBlockEntity(pos)).configure(newId, 0x334455));
            h.assertTrue(!placed && ((IdWireEntity)level.getBlockEntity(pos)).wireId().equals(oldId), "denied metadata-only repair restores the original identity");
            h.assertTrue(WireCatalog.get(level.getServer()).count(oldId) == 1 && WireCatalog.get(level.getServer()).count(newId) == 0, "cancelled repair leaves catalog unchanged");
        } finally { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(deny); }
        h.assertTrue(ProtectedRepairPlacement.place(level, pos, state, player, () -> true, () -> ((IdWireEntity)level.getBlockEntity(pos)).configure(newId, 0x334455)), "allowed metadata-only repair commits");
        h.assertTrue(WireCatalog.get(level.getServer()).count(oldId) == 0 && WireCatalog.get(level.getServer()).count(newId) == 1, "repair updates physical membership only at commit");
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); h.succeed();
    }
    @GameTest(template="empty", batch="id_wire")
    public static void cancelledItemPlacementDoesNotCreateId(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(1, 2, 1));
        var player = new net.minecraftforge.common.util.FakePlayer(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "wire-place-test"));
        player.getAbilities().mayBuild = true;
        var stack = new ItemStack(ModItems.ID_WIRE.get(), 3); IdWireItem.configure(stack, "762409", 0x123456);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        var context = new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), Direction.UP, pos, false));
        java.util.function.Consumer<net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent> deny = event -> { if (event.getEntity() == player) event.setCanceled(true); };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST, false, net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent.class, deny);
        try {
            net.minecraftforge.common.ForgeHooks.onPlaceItemIntoWorld(context);
            h.assertTrue(level.getBlockState(pos).isAir() && stack.getCount() == 3, "Forge cancellation restores placement and inventory");
            h.assertTrue(WireCatalog.get(level.getServer()).count("762409") == 0, "denied placement never registers ID");
        } finally { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(deny); }
        net.minecraftforge.common.ForgeHooks.onPlaceItemIntoWorld(context);
        h.assertTrue(WireCatalog.get(level.getServer()).count("762409") == 1, "committed Forge placement registers exactly once");
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); h.succeed();
    }
    @GameTest(template="empty", batch="id_wire")
    public static void networkReloadRetainsIsolation(GameTestHelper h) {
        var level = h.getLevel(); var a = h.absolutePos(new BlockPos(1, 2, 1)); var b = a.east();
        var input = a.north(); var output = b.south();
        put(h, a, "5432101", 0x010203); put(h, b, "5432102", 0x010203);
        level.setBlock(input, ModBlocks.INPUT_ENDPOINT.get().defaultBlockState(), 3);
        level.setBlock(output, ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState(), 3);
        var elements = CompiledElementFactory.create(level, Set.of(a, b), Set.of(input), Set.of(output));
        var original = new CompiledNetwork(801, "isolated", 0, level.dimension().location().toString(), elements);
        var restored = CompiledNetwork.load(original.save()); restored.restoreLogic(); restored.runtimeState(true, true); restored.evaluateLogic(Map.of(input, true));
        h.assertTrue(restored.getEffectiveSignal(output) == 0, "persisted graph does not short adjacent channels on reload");
        var corrupt = elements.stream().filter(e -> e.getPos().equals(a)).findFirst().orElseThrow().save(); corrupt.remove("wireId");
        h.assertTrue(CompiledCircuitElement.load(corrupt).resolveState().status() == CompiledBlockStateCodec.Status.UNRESOLVED, "missing saved identity fails closed");
        for (var pos : List.of(a, b, input, output)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        h.succeed();
    }

    private static CompiledCircuitElement savedWire(GameTestHelper h, BlockPos pos, String id, int color) {
        put(h, pos, id, color);
        var element = new CompiledCircuitElement(1, pos, CircuitElementType.WIRE, CompiledBlockStateCodec.capture(h.getLevel().getBlockState(pos))).captureWire(h.getLevel());
        h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        return element;
    }
    @GameTest(template="empty", batch="id_wire")
    public static void compiledBrokenIdRetainedUntilDecompile(GameTestHelper h) {
        String id = "8391001", dimension = h.getLevel().dimension().location().toString();
        var pos = h.absolutePos(new BlockPos(1, 2, 1));
        var element = savedWire(h, pos, id, 0x123456);
        var networks = new NetworkSavedData(); var catalog = new WireCatalog();
        catalog.placed(dimension, pos, id, 0xabcdef);
        networks.addNetwork(new CompiledNetwork(1, "retained", 0, dimension, List.of(element)));
        catalog.syncCompiled(networks);
        h.assertTrue(catalog.isColorLocked(id) && catalog.color(id, 0) == 0xabcdef, "compile locks current catalog color, not stale block fallback");
        catalog.remove(dimension, pos);
        h.assertTrue(catalog.count(id) == 0 && catalog.colors().containsKey(id), "broken compiled wire remains in catalog without physical copies");
        catalog.recolor(id, 0xffffff);
        h.assertTrue(catalog.color(id, 0) == 0xabcdef, "server refuses recolor of broken compiled ID");
        networks.removeNetwork(1); catalog.syncCompiled(networks);
        h.assertTrue(!catalog.colors().containsKey(id) && !catalog.isColorLocked(id), "decompile releases broken-only ID immediately");
        h.succeed();
    }
    @GameTest(template="empty", batch="id_wire")
    public static void sharedIdStaysLockedUntilLastNetwork(GameTestHelper h) {
        String id = "8391002", dimension = h.getLevel().dimension().location().toString();
        var a = h.absolutePos(new BlockPos(1, 2, 1)); var b = a.east();
        var networks = new NetworkSavedData(); var catalog = new WireCatalog();
        var first = savedWire(h, a, id, 0x112233); var second = savedWire(h, b, id, 0x112233);
        catalog.placed(dimension, a, id, 0x112233); catalog.placed(dimension, b, id, 0x112233);
        networks.addNetworks(List.of(new CompiledNetwork(1, "a", 0, dimension, List.of(first)), new CompiledNetwork(2, "b", 0, dimension, List.of(second))));
        catalog.syncCompiled(networks); catalog.remove(dimension, b);
        networks.removeNetwork(1); catalog.syncCompiled(networks);
        h.assertTrue(catalog.isColorLocked(id), "remaining compiled owner keeps even uncompiled physical copies color-locked");
        networks.removeNetworks(List.of(2)); catalog.syncCompiled(networks);
        h.assertTrue(!catalog.isColorLocked(id) && catalog.colors().containsKey(id), "last decompile unlocks but preserves physical ID");
        catalog.recolor(id, 0xaabbcc);
        h.assertTrue(catalog.color(id, 0) == 0xaabbcc, "color edits work again after final decompile");
        catalog.remove(dimension, a);
        h.assertTrue(!catalog.colors().containsKey(id), "uncompiled final removal still deletes ID"); h.succeed();
    }
    @GameTest(template="empty", batch="id_wire")
    public static void brokenCompiledPaletteSurvivesReload(GameTestHelper h) {
        String id = "8391003", dimension = h.getLevel().dimension().location().toString(); var pos = h.absolutePos(new BlockPos(1, 2, 1));
        var element = savedWire(h, pos, id, 0x111111); var networks = new NetworkSavedData(); var catalog = new WireCatalog();
        catalog.placed(dimension, pos, id, 0xfedcba);
        networks.addNetwork(new CompiledNetwork(1, "saved", 0, dimension, List.of(element)));
        catalog.syncCompiled(networks); catalog.remove(dimension, pos);
        var restored = WireCatalog.load(catalog.save(new CompoundTag()));
        var restoredNetworks = NetworkSavedData.load(networks.save(new CompoundTag())); restored.syncCompiled(restoredNetworks);
        h.assertTrue(restored.isColorLocked(id) && restored.color(id, 0) == 0xfedcba, "broken-only ID and its current color survive world reload");
        restored.recolor(id, 0); h.assertTrue(restored.color(id, 0) == 0xfedcba, "lock survives reload");
        var migrated = new WireCatalog(); migrated.syncCompiled(restoredNetworks);
        h.assertTrue(migrated.isColorLocked(id) && migrated.colors().containsKey(id), "existing saves recover a previously deleted compiled ID from its snapshot");
        restoredNetworks.removeNetwork(1); restored.syncCompiled(restoredNetworks);
        h.assertTrue(restored.colors().isEmpty(), "no phantom reservation remains after decompiling reloaded network"); h.succeed();
    }
    @GameTest(template="empty", batch="id_wire")
    public static void stagedCompilationOnlyLocksAfterPublish(GameTestHelper h) {
        String id = "8391004", dimension = h.getLevel().dimension().location().toString(); var pos = h.absolutePos(new BlockPos(1, 2, 1));
        var element = savedWire(h, pos, id, 0x123456); var builder = new CompiledNetwork.Builder(); builder.add(element);
        builder.add(new CompiledCircuitElement(2, pos.west(), CircuitElementType.INPUT, CompiledBlockStateCodec.capture(ModBlocks.INPUT_ENDPOINT.get().defaultBlockState())));
        builder.add(new CompiledCircuitElement(3, pos.east(), CircuitElementType.OUTPUT, CompiledBlockStateCodec.capture(ModBlocks.OUTPUT_ENDPOINT.get().defaultBlockState())));
        var candidate = builder.seal("staged", dimension); var networks = new NetworkSavedData(); var catalog = new WireCatalog();
        catalog.placed(dimension, pos, id, 0x123456); var admission = networks.prepareCompilation(candidate);
        for (var e : candidate.getElements()) admission.stage(e);
        while (admission.preparedRuntime.indexNext()) { }
        catalog.syncCompiled(networks); h.assertTrue(!catalog.isColorLocked(id), "unpublished compilation does not lock catalog");
        admission.publish(); catalog.syncCompiled(networks);
        h.assertTrue(catalog.isColorLocked(id), "normal staged publication reserves ID");
        catalog.remove(dimension, pos); h.assertTrue(catalog.colors().containsKey(id), "staged network retains broken wire ID"); h.succeed();
    }
    @GameTest(template="empty", batch="id_wire")
    public static void compiledColorLockPacketRoundTrip(GameTestHelper h) {
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            var packet = new com.example.compiledcircuits.networking.WireCatalogS2CPacket(7, 0, true, Map.of("12", 0x112233, "13", 0x334455), Set.of("12"));
            com.example.compiledcircuits.networking.WireCatalogS2CPacket.encode(packet, buffer);
            var decoded = com.example.compiledcircuits.networking.WireCatalogS2CPacket.decode(buffer);
            h.assertTrue(decoded.equals(packet), "palette snapshot preserves compiled color locks");
        } finally { buffer.release(); }
        h.succeed();
    }
}
