package com.frosty.bedgunwars;

import com.frosty.bedgunwars.command.GameCommand;
import com.frosty.bedgunwars.command.GameDebugCommand;
import com.frosty.bedgunwars.event.BedEventHandler;
import com.frosty.bedgunwars.event.GameTickHandler;
import com.frosty.bedgunwars.event.PlayerDeathHandler;
import com.frosty.bedgunwars.event.PlayerRespawnHandler;
import com.frosty.bedgunwars.game.GameManager;
import com.frosty.bedgunwars.game.GameModeType;
import com.frosty.bedgunwars.game.GamePhase;
import com.frosty.bedgunwars.game.GameSession;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import com.frosty.bedgunwars.client.KeyBindings;
import com.frosty.bedgunwars.network.PacketHandler;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod(BedGunWars.MOD_ID)
public class BedGunWars {
    public static final String MOD_ID = "bedgunwars";

    public BedGunWars() {
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new GameTickHandler());
        MinecraftForge.EVENT_BUS.register(new BedEventHandler());
        MinecraftForge.EVENT_BUS.register(new PlayerDeathHandler());
        MinecraftForge.EVENT_BUS.register(new PlayerRespawnHandler());
        PacketHandler.register();
        System.out.println("BedGunWars Loaded");
    }

    @Mod.EventBusSubscriber(modid = BedGunWars.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void onKeyInput(net.minecraftforge.client.event.InputEvent.Key event) {
            if (KeyBindings.GUN_MENU_KEY.consumeClick()) {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player == null || mc.screen != null) return;
                PacketHandler.CHANNEL.sendToServer(new com.frosty.bedgunwars.network.RequestGunMenuPacket());
            }
        }
    }

    @Mod.EventBusSubscriber(modid = BedGunWars.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onRegisterKeyMappings(net.minecraftforge.client.event.RegisterKeyMappingsEvent event) {
            event.register(KeyBindings.GUN_MENU_KEY);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {

        // Suggests all GamePhase enum values
        SuggestionProvider<CommandSourceStack> phaseSuggestions = (ctx, builder) -> {
            for (GamePhase p : GamePhase.values()) {
                builder.suggest(p.name());
            }
            return builder.buildFuture();
        };

        // Suggests players currently in the active game session
        SuggestionProvider<CommandSourceStack> inGamePlayers = (ctx, builder) -> {
            GameSession session = GameManager.getSession();
            if (session != null && session.isActive()) {
                for (UUID uuid : session.getPlayers()) {
                    ServerPlayer p = ctx.getSource().getServer().getPlayerList().getPlayer(uuid);
                    if (p != null) builder.suggest(p.getName().getString());
                }
            }
            return builder.buildFuture();
        };

        event.getDispatcher().register(
                Commands.literal("game")
                        .then(Commands.literal("start")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("solo")
                                        .executes(ctx -> GameCommand.startGame(ctx.getSource(), GameModeType.SOLO)))
                                .then(Commands.literal("teams")
                                        .executes(ctx -> GameCommand.startGame(ctx.getSource(), GameModeType.TEAMS)))
                        )
                        .then(Commands.literal("join")
                                .executes(ctx -> GameCommand.joinGame(ctx.getSource()))
                        )
                        .then(Commands.literal("leave")
                                .executes(ctx -> GameCommand.leaveGame(ctx.getSource()))
                        )
                        .then(Commands.literal("border")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("size", IntegerArgumentType.integer(11))
                                        .executes(ctx -> GameCommand.setBorder(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "size"))))
                        )
                        .then(Commands.literal("prep")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                        .executes(ctx -> GameCommand.setPrep(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "seconds"))))
                        )
                        .then(Commands.literal("matchtime")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                        .executes(ctx -> GameCommand.setMatchTime(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "seconds"))))
                        )
                        .then(Commands.literal("stop")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> GameCommand.stopGame(ctx.getSource()))
                        )
                        .then(Commands.literal("debug")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("status")
                                        .executes(ctx -> GameDebugCommand.status(ctx.getSource()))
                                )
                                .then(Commands.literal("eliminate")
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .suggests(inGamePlayers)
                                                .executes(ctx -> GameDebugCommand.eliminate(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"))))
                                )
                                .then(Commands.literal("eliminatebed")
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .suggests(inGamePlayers)
                                                .executes(ctx -> GameDebugCommand.eliminateBed(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"))))
                                )
                                .then(Commands.literal("forcewin")
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .suggests(inGamePlayers)
                                                .executes(ctx -> GameDebugCommand.forceWin(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"))))
                                )
                                .then(Commands.literal("setphase")
                                        .then(Commands.argument("phase", StringArgumentType.word())
                                                .suggests(phaseSuggestions)
                                                .executes(ctx -> GameDebugCommand.setPhase(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "phase"))))
                                )
                        )
        );
    }
}