package com.frosty.bedgunwars;

import com.frosty.bedgunwars.client.TabStatsScreen;
import com.frosty.bedgunwars.command.GameCommand;
import com.frosty.bedgunwars.command.GameDebugCommand;
import com.frosty.bedgunwars.event.BedEventHandler;
import com.frosty.bedgunwars.event.GameTickHandler;
import com.frosty.bedgunwars.event.PlayerDeathHandler;
// import com.frosty.bedgunwars.event.PlayerRespawnHandler;
import com.frosty.bedgunwars.game.*;
import com.frosty.bedgunwars.minimap.MinimapConfig;
import com.frosty.bedgunwars.minimap.MinimapRenderer;
import com.frosty.bedgunwars.minimap.MinimapSettingsScreen;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

    public BedGunWars(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext context) {
        ExcludedGunsConfig.load();
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new GameTickHandler());
        MinecraftForge.EVENT_BUS.register(new BedEventHandler());
        MinecraftForge.EVENT_BUS.register(new PlayerDeathHandler());
        SOUNDS.register(context.getModEventBus());
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            MinecraftForge.EVENT_BUS.register(new com.frosty.bedgunwars.minimap.MinimapRenderer());
            com.frosty.bedgunwars.client.TabStatsClientProxy.register();
            com.frosty.bedgunwars.client.KillstreakClientProxy.register();
        }
        MinimapConfig.register(context);
        PacketHandler.register();
        System.out.println("BedGunWars Loaded");
    }

    public static final net.minecraftforge.registries.DeferredRegister<net.minecraft.sounds.SoundEvent> SOUNDS =
            net.minecraftforge.registries.DeferredRegister.create(
                    net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS, MOD_ID);

    public static final net.minecraftforge.registries.RegistryObject<net.minecraft.sounds.SoundEvent> UAV_SELF =
            SOUNDS.register("uav_self", () ->
                    net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "uav_self")));

    public static final net.minecraftforge.registries.RegistryObject<net.minecraft.sounds.SoundEvent> UAV_ENEMY =
            SOUNDS.register("uav_enemy", () ->
                    net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "uav_enemy")));

    public static final net.minecraftforge.registries.RegistryObject<net.minecraft.sounds.SoundEvent> AIRSTRIKE_SOUND =
            SOUNDS.register("airstrike", () ->
                    net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "airstrike")));

    public static final net.minecraftforge.registries.RegistryObject<net.minecraft.sounds.SoundEvent> JET_SOUND =
            SOUNDS.register("jet_engine", () ->
                    net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "jet_engine")));

    public static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger();
    public static boolean debugLogging = false;

    public static void debugLog(String message, Object... args) {
        if (debugLogging) LOGGER.info(message, args);
    }

    @Mod.EventBusSubscriber(modid = BedGunWars.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void onKeyInput(net.minecraftforge.client.event.InputEvent.Key event) {
            if (KeyBindings.GUN_MENU_KEY.consumeClick()) {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                BedGunWars.LOGGER.info("[GunMenu] B pressed: player={}, screen={}", mc.player, mc.screen);
                if (mc.player == null || mc.screen != null) return;
                PacketHandler.CHANNEL.sendToServer(new com.frosty.bedgunwars.network.RequestGunMenuPacket());
            }
            if (KeyBindings.MINIMAP_SETTINGS_KEY.consumeClick()) {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player == null || mc.screen != null) return;
                mc.setScreen(new MinimapSettingsScreen());
            }
            // TAB stats — hold to show, release to close, only during active game
            if (event.getKey() == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
                net.minecraft.client.Minecraft mc2 = net.minecraft.client.Minecraft.getInstance();
                BedGunWars.LOGGER.info("[Tab] pressed: isStarted={}, hasData={}",
                        com.frosty.bedgunwars.minimap.MinimapRenderer.isStarted(),
                        com.frosty.bedgunwars.client.TabStatsScreen.hasData());
                if (mc2.player != null && com.frosty.bedgunwars.minimap.MinimapRenderer.isStarted()) {
                    if (event.getAction() == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
                        com.frosty.bedgunwars.client.TabStatsScreen.visible = true;
                        BedGunWars.LOGGER.info("[Tab] visible=true, hasData={}, isStarted={}",
                                com.frosty.bedgunwars.client.TabStatsScreen.hasData(),
                                com.frosty.bedgunwars.minimap.MinimapRenderer.isStarted());
                    } else if (event.getAction() == org.lwjgl.glfw.GLFW.GLFW_RELEASE) {
                        com.frosty.bedgunwars.client.TabStatsScreen.visible = false;
                    }
                }
            }
            // V key — killstreak activation overlay
            if (com.frosty.bedgunwars.minimap.MinimapRenderer.isStarted()) {
                if (KeyBindings.KILLSTREAK_KEY.consumeClick()) {
                    com.frosty.bedgunwars.client.KillstreakHudRenderer.overlayOpen =
                            !com.frosty.bedgunwars.client.KillstreakHudRenderer.overlayOpen;
                }
            }
        }

        @SubscribeEvent
        public static void onScroll(net.minecraftforge.client.event.InputEvent.MouseScrollingEvent event) {
            if (com.frosty.bedgunwars.client.KillstreakHudRenderer.overlayOpen) {
                com.frosty.bedgunwars.client.KillstreakHudRenderer.scrollSelection(event.getScrollDelta());
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onMouseClick(net.minecraftforge.client.event.InputEvent.MouseButton event) {
            if (event.getAction() != org.lwjgl.glfw.GLFW.GLFW_PRESS) return;
            // Killstreak overlay left-click to activate
            if (com.frosty.bedgunwars.client.KillstreakHudRenderer.overlayOpen && event.getButton() == 0) {
                com.frosty.bedgunwars.client.KillstreakHudRenderer.activateSelected();
            }
        }
    }

    @Mod.EventBusSubscriber(modid = BedGunWars.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onRegisterKeyMappings(net.minecraftforge.client.event.RegisterKeyMappingsEvent event) {
            event.register(KeyBindings.GUN_MENU_KEY);
            event.register(KeyBindings.MINIMAP_SETTINGS_KEY);
            event.register(KeyBindings.KILLSTREAK_KEY);
//            event.register(KeyBindings.TAB_STATS_KEY);
        }
//        @SubscribeEvent
//        public static void onRegisterRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
//            event.registerEntityRenderer(BedGunWars.JET.get(),
//                    com.frosty.bedgunwars.entity.JetRenderer::new);
//        }

        @SubscribeEvent
        public static void onRegisterLayerDefinitions(
                net.minecraftforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(
                    com.frosty.bedgunwars.entity.JetModel.LAYER_LOCATION,
                    com.frosty.bedgunwars.entity.JetModel::createBodyLayer);
        }

//        @SubscribeEvent
//        public static void onRegisterAdditionalModels(net.minecraftforge.client.event.ModelEvent.RegisterAdditional event) {
//            event.register(ResourceLocation.fromNamespaceAndPath("bedgunwars", "jet"));
//        }
    }

    @SubscribeEvent
    public void onServerStarting(net.minecraftforge.event.server.ServerStartingEvent event) {
        com.frosty.bedgunwars.game.LoadoutManager.init(event.getServer());
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
                                        .executes(ctx -> GameCommand.startGame(ctx.getSource(), GameModeType.SOLO, 1)))
                                .then(Commands.literal("teams")
                                        .then(Commands.argument("count", IntegerArgumentType.integer(2, 6))
                                                .executes(ctx -> GameCommand.startGame(ctx.getSource(), GameModeType.TEAMS,
                                                        IntegerArgumentType.getInteger(ctx, "count"))))
                                )
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
                        .then(Commands.literal("teamoptions")
                                .then(Commands.literal("setfriendlyfire")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                .executes(ctx -> GameCommand.setFriendlyFire(ctx.getSource(),
                                                        BoolArgumentType.getBool(ctx, "enabled")))))
                        )
                        .then(Commands.literal("stop")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> GameCommand.stopGame(ctx.getSource()))
                        )
                        .then(Commands.literal("forcejoinall")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> GameCommand.forceJoinAll(ctx.getSource())))
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
                                                        StringArgumentType.getString(ctx, "phase")))
                                        )
                                )
                                .then(Commands.literal("forcebordershrink")
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                                .executes(ctx -> GameDebugCommand.forceBorderShrink(
                                                        ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "seconds"))))
                                )
                                .then(Commands.literal("listtaczitems")
                                        .executes(ctx -> GameDebugCommand.listTaczItems(ctx.getSource())))
                                .then(Commands.literal("givemoney")
                                        .then(Commands.argument("target", StringArgumentType.word())
                                                .then(Commands.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                                        .executes(ctx -> GameDebugCommand.giveMoney(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "target"),
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "amount"))))))
                                .then(Commands.literal("enableDebugLog")
                                        .then(Commands.argument("enabled", IntegerArgumentType.integer(0, 1))
                                                .executes(ctx -> {
                                                    int val = IntegerArgumentType.getInteger(ctx, "enabled");
                                                    BedGunWars.debugLogging = val == 1;
                                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                                            "Debug logging " + (BedGunWars.debugLogging ? "§aenabled" : "§cdisabled")), false);
                                                    return 1;
                                                })))
                                .then(Commands.literal("givekillstreak")
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .suggests(inGamePlayers)
                                                .then(Commands.argument("killstreak", StringArgumentType.word())
                                                        .suggests((ctx, builder) -> {
                                                            for (com.frosty.bedgunwars.game.KillstreakType t :
                                                                    com.frosty.bedgunwars.game.KillstreakType.values())
                                                                builder.suggest(t.name().toLowerCase());
                                                            return builder.buildFuture();
                                                        })
                                                        .executes(ctx -> GameDebugCommand.giveKillstreak(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "player"),
                                                                StringArgumentType.getString(ctx, "killstreak"))))))
                                .then(Commands.literal("spawnjet")
                                        .executes(ctx -> GameDebugCommand.spawnJet(ctx.getSource())))
                                .then(Commands.literal("despawnjet")
                                        .executes(ctx -> GameDebugCommand.despawnJets(ctx.getSource())))
                                .then(Commands.literal("excludegun")
                                        .then(Commands.literal("reload")
                                                .executes(ctx -> {
                                                    ExcludedGunsConfig.load();
                                                    ctx.getSource().sendSuccess(() ->
                                                            Component.literal("§aExcluded guns config reloaded."), false);
                                                    return 1;
                                                }))
                                        .then(Commands.literal("list")
                                                .executes(ctx -> {
                                                    ExcludedGunsConfig.getExcluded().forEach(id ->
                                                            ctx.getSource().sendSuccess(() ->
                                                                    Component.literal("§7- " + id), false));
                                                    return 1;
                                                })))
                        )

        );
    }
}