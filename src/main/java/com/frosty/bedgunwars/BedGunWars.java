package com.frosty.bedgunwars;

import com.frosty.bedgunwars.command.GameCommand;
import com.frosty.bedgunwars.command.GameDebugCommand;
import com.frosty.bedgunwars.event.BedEventHandler;
import com.frosty.bedgunwars.event.GameTickHandler;
import com.frosty.bedgunwars.event.PlayerDeathHandler;
import com.frosty.bedgunwars.event.PlayerRespawnHandler;
import com.frosty.bedgunwars.game.GameModeType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.mojang.brigadier.arguments.StringArgumentType;

@Mod(BedGunWars.MOD_ID)
public class BedGunWars {
    public static final String MOD_ID = "bedgunwars";

    public BedGunWars() {
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new GameTickHandler());
        MinecraftForge.EVENT_BUS.register(new BedEventHandler());
        MinecraftForge.EVENT_BUS.register(new PlayerDeathHandler());
        MinecraftForge.EVENT_BUS.register(new PlayerRespawnHandler());
        System.out.println("BedGunWars Loaded");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
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
                                                IntegerArgumentType.getInteger(ctx, "size")
                                        ))
                                )
                        )
                        .then(Commands.literal("prep")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                        .executes(ctx -> GameCommand.setPrep(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "seconds")
                                        ))
                                )
                        )
                        .then(Commands.literal("stop")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> GameCommand.stopGame(ctx.getSource()))
                        )
                        .then(Commands.literal("debug")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("eliminate")
                                        .executes(ctx -> GameDebugCommand.eliminate(ctx.getSource()))
                                )
                                .then(Commands.literal("forcewin")
                                        .executes(ctx -> GameDebugCommand.forceWin(ctx.getSource()))
                                )
                                .then(Commands.literal("setphase")
                                        .then(Commands.argument("phase", StringArgumentType.word())
                                                .executes(ctx -> GameDebugCommand.setPhase(ctx.getSource(), StringArgumentType.getString(ctx, "phase")))
                                        )
                                )
                        )
        );
    }
}