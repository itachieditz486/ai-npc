package com.ravex.civilization.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.ravex.civilization.RavexEvents;
import com.ravex.civilization.npc.NpcBrain;
import com.ravex.civilization.npc.NpcEntity;
import com.ravex.civilization.npc.NpcNames;
import com.ravex.civilization.npc.NpcWorldData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** /npc <name> <message>   /npc list   /npc release_yassine (op) */
public final class NpcCommand {
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("npc")
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("release_yassine").requires(s -> s.hasPermission(2))
                        .executes(ctx -> release(ctx.getSource())))
                .then(Commands.argument("name", StringArgumentType.string())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(names(c.getSource().getServer()), b))
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> talk(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name"),
                                        StringArgumentType.getString(ctx, "message"))))));
    }

    private static List<String> names(MinecraftServer server) {
        List<String> out = new ArrayList<>();
        for (NpcEntity n : RavexEvents.allNpcs(server)) out.add(n.npcName());
        return out;
    }

    private static int talk(CommandSourceStack src, String name, String message) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        boolean found = false;
        for (NpcEntity npc : RavexEvents.allNpcs(src.getServer())) {
            if (NpcNames.mentions(npc.npcName(), name) || npc.npcName().equalsIgnoreCase(name)) {
                NpcBrain.handle(npc, player, name + ", " + message);
                found = true;
            }
        }
        if (!found) src.sendFailure(Component.literal("No loaded NPC named '" + name + "'. Try /npc list."));
        return found ? 1 : 0;
    }

    private static int list(CommandSourceStack src) {
        List<NpcEntity> all = RavexEvents.allNpcs(src.getServer());
        if (all.isEmpty()) {
            src.sendSuccess(() -> Component.literal("No loaded NPCs."), false);
            return 0;
        }
        for (NpcEntity n : all) {
            String line = String.format("%s - HP %.0f/%.0f, at %d %d %d, mission: %s (%d/%d), inventory: %s",
                    n.npcName(), n.getHealth(), n.getMaxHealth(), n.getBlockX(), n.getBlockY(), n.getBlockZ(),
                    n.mission().name().toLowerCase(), n.missionProgress(), n.missionTarget(), n.inventorySummary());
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return all.size();
    }

    private static int release(CommandSourceStack src) {
        NpcWorldData.get(src.getServer()).setYassineEntity(null);
        src.sendSuccess(() -> Component.literal("Yassine's location lock cleared. His identity and memory are kept."), true);
        return 1;
    }

    private NpcCommand() {}
}
