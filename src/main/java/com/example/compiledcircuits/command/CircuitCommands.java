package com.example.compiledcircuits.command;

import com.example.compiledcircuits.diagnostics.PerformanceDiagnostics;
import com.example.compiledcircuits.network.CompiledNetwork;
import com.example.compiledcircuits.network.CompiledCircuitElement;
import com.example.compiledcircuits.network.CompiledElementFactory;
import com.example.compiledcircuits.network.NetworkOperations;
import java.util.List;
import com.example.compiledcircuits.network.NetworkSavedData;
import com.example.compiledcircuits.network.NetworkScanner;
import com.example.compiledcircuits.network.NetworkSelectionData;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import com.example.compiledcircuits.networking.ModNetworking;
import com.example.compiledcircuits.networking.OpenCompileNameS2CPacket;
import com.example.compiledcircuits.networking.NetworkListS2CPacket;
import net.minecraftforge.network.PacketDistributor;

import com.example.compiledcircuits.networking.NetworkGuiSync;

public final class CircuitCommands {

    private CircuitCommands() {
    }

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {

        dispatcher.register(
                Commands.literal("circuit")
                        .then(Commands.literal("conflicts").requires(source -> source.hasPermission(2))
                                .then(Commands.literal("list").executes(context -> listConflicts(context.getSource(),0)).then(Commands.argument("offset",IntegerArgumentType.integer(0)).executes(c->listConflicts(c.getSource(),IntegerArgumentType.getInteger(c,"offset")))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("record", StringArgumentType.word())
                                                .executes(context -> removeConflict(context.getSource(),
                                                        StringArgumentType.getString(context, "record"))))))

                        .then(
                                Commands.literal("compile")
                                        .executes(context ->
                                                compile(
                                                        context.getSource()
                                                )
                                        )
                        )

                        .then(Commands.literal("capacity").executes(c->capacity(c.getSource())))
                        .then(Commands.literal("debug_elements")
                                .executes(context -> debugElements(context.getSource())))

                        .then(Commands.literal("open_selected")
                                .executes(context -> openSelectedNetwork(context.getSource())))

                        .then(Commands.literal("compile_named")
                                .executes(context -> openCompileName(context.getSource())))

                        .then(
                                Commands.literal("decompile")
                                        .then(Commands.argument("id",IntegerArgumentType.integer(1)).executes(c->NetworkOperations.reply(c.getSource(),request(c.getSource(),NetworkOperations.Action.DECOMPILE,List.of(IntegerArgumentType.getInteger(c,"id")),0,""))))
                                        .executes(context ->
                                                decompile(
                                                        context.getSource()
                                                )
                                        )
                        )

                        .then(
                                Commands.literal("list")
                                        .then(Commands.argument("after",IntegerArgumentType.integer(0)).executes(c->listNetworks(c.getSource(),IntegerArgumentType.getInteger(c,"after"))))
                                        .executes(context ->
                                                listNetworks(
                                                        context.getSource()
                                                )
                                        )
                        )
                        .then(
                                Commands.literal("rename")
                                        .then(
                                                Commands.argument(
                                                                "id",
                                                                IntegerArgumentType.integer(1)
                                                        )
                                                        .then(
                                                                Commands.argument(
                                                                                "name",
                                                                                StringArgumentType.greedyString()
                                                                        )
                                                                        .executes(context ->
                                                                                renameNetwork(
                                                                                        context.getSource(),
                                                                                        IntegerArgumentType.getInteger(
                                                                                                context,
                                                                                                "id"
                                                                                        ),
                                                                                        StringArgumentType.getString(
                                                                                                context,
                                                                                                "name"
                                                                                        )
                                                                                )
                                                                        )
                                                        )
                                        )
                        )

                        .then(
                                Commands.literal("folder")
                                        .then(
                                                Commands.argument(
                                                                "id",
                                                                IntegerArgumentType.integer(1)
                                                        )
                                                        .then(
                                                                Commands.argument(
                                                                                "path",
                                                                                StringArgumentType.greedyString()
                                                                        )
                                                                        .executes(context ->
                                                                                moveNetworkToFolder(
                                                                                        context.getSource(),
                                                                                        IntegerArgumentType.getInteger(
                                                                                                context,
                                                                                                "id"
                                                                                        ),
                                                                                        StringArgumentType.getString(
                                                                                                context,
                                                                                                "path"
                                                                                        )
                                                                                )
                                                                        )
                                                        )
                                        )
                        )
                        .then(
                                Commands.literal("gui")
                                        .executes(context ->
                                                openGui(context.getSource())
                                        )
                        )
        );
    }

    private static NetworkOperations.Result request(CommandSourceStack source, NetworkOperations.Action action,
                                                     List<Integer> ids, int target, String value) {
        return NetworkOperations.execute(source, action, ids, target, value);
    }
    private static int listConflicts(CommandSourceStack source,int offset) {
        var result = request(source, NetworkOperations.Action.LIST_CONFLICTS, List.of(), 0, "");
        if (!result.success()) return NetworkOperations.reply(source,result);
        var data = NetworkSavedData.get(source.getServer());
        source.sendSuccess(() -> Component.literal("Blocked saved records: " + data.getInvalidMembershipRecordCount()),false);
        for (String line : data.getInvalidMembershipSummaries(offset,20)) source.sendSuccess(() -> Component.literal(line),false);
        if((long)offset+20<data.getInvalidMembershipRecordCount())source.sendSuccess(()->Component.literal("Next: /circuit conflicts list "+(offset+20)),false);
        return data.getInvalidMembershipRecordCount();
    }
    private static int removeConflict(CommandSourceStack source, String record) {
        return NetworkOperations.reply(source,request(source,NetworkOperations.Action.REMOVE_CONFLICT,List.of(),0,record));
    }
    private static int openCompileName(CommandSourceStack source) {
        var result = request(source,NetworkOperations.Action.OPEN_COMPILE,List.of(),0,"");
        if (!result.success()) return NetworkOperations.reply(source,result);
        var player = (ServerPlayer)source.getEntity();
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),new OpenCompileNameS2CPacket());
        return 1;
    }
    private static int openSelectedNetwork(CommandSourceStack source) {
        var result = request(source,NetworkOperations.Action.SELECTED,List.of(),0,"");
        if (!result.success()) return NetworkOperations.reply(source,result);
        NetworkGuiSync.sendListAndNavigate((ServerPlayer)source.getEntity(),result.networks().get(0).getId());
        return 1;
    }
    private static int debugElements(CommandSourceStack source) {
        var result = request(source,NetworkOperations.Action.DEBUG,List.of(),0,"");
        if (!result.success()) return NetworkOperations.reply(source,result);
        var network = result.networks().get(0);
        source.sendSuccess(() -> Component.literal("Network #" + network.getId() + " " + network.getName()),false);
        int count=0;
        for (var e:network.getElements()) {
            if (count++ >=20) break;
            source.sendSuccess(() -> Component.literal("#"+e.getId()+" "+e.getType()+" "+e.getBlockId()+" @ "+e.getPos().toShortString()),false);
        }
        return 1;
    }
    private static int compile(CommandSourceStack source) {
        long started=PerformanceDiagnostics.begin(); PerformanceDiagnostics.add("compile.command.calls",1);
        String outcome="compile.command.exceptions";
        try {
            var response=request(source,NetworkOperations.Action.COMPILE,List.of(),0,null);
            int result=NetworkOperations.reply(source,response);
            outcome=response.code()==NetworkOperations.Code.QUEUED?"compile.command.queued"
                    : result>0?"compile.command.success":"compile.command.rejected";
            return result;
        } finally { PerformanceDiagnostics.elapsed("compile.command",started); PerformanceDiagnostics.add(outcome,1); }
    }
    private static int decompile(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player))
            return NetworkOperations.reply(source,request(source,NetworkOperations.Action.DECOMPILE,List.of(),0,""));
        var pos=NetworkSelectionData.get(player);
        var network=pos==null?null:NetworkSavedData.get(source.getServer()).findNetworkContaining(player.serverLevel(),pos);
        return NetworkOperations.reply(source,request(source,NetworkOperations.Action.DECOMPILE,
                network==null?List.of():List.of(network.getId()),0,""));
    }
    private static int capacity(CommandSourceStack source){
        var r=request(source,NetworkOperations.Action.LIST,List.of(),0,"");if(!r.success())return NetworkOperations.reply(source,r);
        var data=NetworkSavedData.get(source.getServer());String dim=source.getLevel().dimension().location().toString();var u=data.capacity().usage(dim);
        source.sendSuccess(()->Component.literal("Membership "+dim+": "+u.state()+", records="+u.records()+"/"+u.limit()+", network limit="+data.capacity().networkLimit()+", oversized networks="+data.capacity().oversizedNetworks(dim)+", snapshot upper bytes="+u.encodedUpperBound()+", parts="+u.parts()),false);return 1;
    }
    private static int listNetworks(CommandSourceStack source){return listNetworks(source,0);}
    private static int listNetworks(CommandSourceStack source,int after){
        var result=request(source,NetworkOperations.Action.LIST,List.of(),0,"");if(!result.success())return NetworkOperations.reply(source,result);
        var data=NetworkSavedData.get(source.getServer());var page=data.capacity().page(after,20);
        source.sendSuccess(()->Component.literal("Compiled networks: "+data.getNetworks().size()+"; IDs after "+after),false);
        for(var n:page)source.sendSuccess(()->Component.literal("#"+n.getId()+" "+n.getName().substring(0,Math.min(64,n.getName().length()))+" "+n.getDimension()+" elements="+n.getElements().size()+(data.capacity().oversized(n)?" OVER_CAPACITY":"")),false);
        if(page.size()==20)source.sendSuccess(()->Component.literal("Next: /circuit list "+page.get(page.size()-1).getId()),false);return 1;
    }
    private static int renameNetwork(CommandSourceStack source,int id,String name) {
        return NetworkOperations.reply(source,request(source,NetworkOperations.Action.RENAME_NETWORK,List.of(id),0,name));
    }
    private static int moveNetworkToFolder(CommandSourceStack source,int id,String path) {
        return NetworkOperations.reply(source,request(source,NetworkOperations.Action.MOVE_PATH,List.of(id),0,path));
    }
    private static int openGui(CommandSourceStack source) {
        var result=request(source,NetworkOperations.Action.GUI,List.of(),0,"");
        if (!result.success()) return NetworkOperations.reply(source,result);
        NetworkGuiSync.sendList((ServerPlayer)source.getEntity()); return 1;
    }
}
