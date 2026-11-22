package dev.selestor1462.cannonencoder.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.selestor1462.cannonencoder.client.easyplace.EasyPlaceManager;
import dev.selestor1462.cannonencoder.client.gui.PigCannonScreen;
import dev.selestor1462.cannonencoder.client.validation.EncodingError;
import dev.selestor1462.cannonencoder.client.validation.EncodingValidator;
import dev.selestor1462.cannonencoder.client.validation.ValidationReport;
import dev.selestor1462.cannonencoder.state.AnchorState;
import dev.selestor1462.cannonencoder.state.CannonMode;
import dev.selestor1462.cannonencoder.state.ClientStateStore;
import dev.selestor1462.cannonencoder.state.SessionState;
import dev.selestor1462.cannonencoder.template.TemplateRegistry;
import java.util.Optional;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;

public final class CannonCommand {
    public static final String PREFIX = "§6[CannonEncoder] §r";

    private CannonCommand() {
    }

    public static void register(
        CommandDispatcher<FabricClientCommandSource> dispatcher,
        HolderLookup.Provider registryAccess,
        ClientStateStore stateStore,
        HighlightManager highlightManager,
        EasyPlaceManager easyPlaceManager,
        CannonStatusHudManager hudManager
    ) {
        LiteralArgumentBuilder<FabricClientCommandSource> node = ClientCommandManager.literal("cannon")
            .executes(context -> openGui(stateStore, highlightManager, easyPlaceManager))
            .then(ClientCommandManager.literal("gui")
                .executes(context -> openGui(stateStore, highlightManager, easyPlaceManager)))
            .then(ClientCommandManager.literal("clear")
                .executes(context -> clearAll(context.getSource(), stateStore, highlightManager)))
            .then(ClientCommandManager.literal("hud")
                .then(ClientCommandManager.literal("toggle")
                    .executes(context -> toggleHud(context.getSource(), hudManager))))
            .then(ClientCommandManager.literal("easyplace")
                .executes(context -> toggleEasyPlace(context.getSource(), stateStore, easyPlaceManager)))
            .then(ClientCommandManager.literal("check")
                .executes(context -> validateEncoding(context.getSource(), stateStore, highlightManager.templateRegistry())))
            .then(ClientCommandManager.literal("toggle")
                .executes(context -> toggleHighlight(context.getSource(), stateStore, highlightManager)))
            .then(ClientCommandManager.literal("tp")
                .executes(context -> tpLanding(context.getSource(), stateStore)))
            .then(ClientCommandManager.literal("anchor")
                .then(ClientCommandManager.literal("auto")
                    .executes(context -> autoDetectAnchor(context.getSource(), stateStore, highlightManager)))
                .then(ClientCommandManager.literal("set")
                    .then(ClientCommandManager.literal("here")
                        .executes(context -> setAnchorHere(context.getSource(), stateStore, highlightManager)))
                    .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                        .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                            .executes(context -> setAnchor(
                                context.getSource(),
                                stateStore,
                                highlightManager,
                                IntegerArgumentType.getInteger(context, "x"),
                                IntegerArgumentType.getInteger(context, "z")
                            ))
                        )
                    )
                )
                .then(ClientCommandManager.literal("clear")
                    .executes(context -> clearAnchor(context.getSource(), stateStore, highlightManager)))
                .then(ClientCommandManager.literal("show")
                    .executes(context -> showAnchor(context.getSource(), stateStore)))
            );

        dispatcher.register(node);
    }

    private static int openGui(
        ClientStateStore stateStore,
        HighlightManager highlightManager,
        EasyPlaceManager easyPlaceManager
    ) {
        PigCannonEncoderClient.setScreenNextTick(() -> new PigCannonScreen(stateStore, highlightManager, easyPlaceManager));
        return 1;
    }

    private static int toggleEasyPlace(FabricClientCommandSource source, ClientStateStore stateStore, EasyPlaceManager easyPlaceManager) {
        if (easyPlaceManager == null) {
            source.sendFeedback(Component.literal(PREFIX + "§cEasyPlace manager not available."));
            return 0;
        }
        boolean enabled = easyPlaceManager.toggle();
        ClientSessionContext context = requireSessionContext(source);
        if (context != null && stateStore != null) {
            SessionState state = stateStore.get(context.environmentKey()).withEasyPlace(enabled);
            stateStore.set(context.environmentKey(), state);
        }
        source.sendFeedback(Component.literal(PREFIX + (enabled ? "§a✔ EasyPlace enabled." : "§cEasyPlace disabled.")));
        return 1;
    }

    private static int tpLanding(FabricClientCommandSource source, ClientStateStore stateStore) {
        ClientSessionContext context = requireSessionContext(source);
        if (context == null) return 0;

        SessionState state = stateStore.get(context.environmentKey());
        if (state.lastCalculation() == null) {
            source.sendFeedback(Component.literal(PREFIX + "§eNo active calculation found. Open GUI to calculate."));
            return 0;
        }

        BlockPos targetPos = new BlockPos(state.lastCalculation().landingX(), 256, state.lastCalculation().landingZ());
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.player.connection != null) {
            client.player.connection.sendCommand(
                String.format(java.util.Locale.ROOT, "tp %d %d %d", targetPos.getX(), targetPos.getY(), targetPos.getZ())
            );
            source.sendFeedback(Component.literal(PREFIX + "§aTeleporting to landing pos at " + targetPos.toShortString() + "..."));
            return 1;
        }
        return 0;
    }

    private static int setAnchor(
        FabricClientCommandSource source,
        ClientStateStore stateStore,
        HighlightManager highlightManager,
        int x,
        int z
    ) {
        ClientSessionContext context = requireSessionContext(source);
        if (context == null) return 0;

        SessionState state = stateStore.get(context.environmentKey());
        BlockPos pos = new BlockPos(x, 0, z);

        AnchorState anchor = new AnchorState(pos, context.dimensionId(), TemplateRegistry.CANNON_V1_DEFAULT);
        SessionState updated = state.withAnchor(anchor);
        stateStore.set(context.environmentKey(), updated);
        source.sendFeedback(Component.literal(PREFIX + "§aPig Cannon Anchor saved at X=" + x + ", Z=" + z));
        if (updated.lastCalculation() != null) {
            highlightManager.apply(context, updated, updated.lastCalculation(), updated.selectedRow(), updated.selectedCol());
        }
        return 1;
    }

    private static int setAnchorHere(FabricClientCommandSource source, ClientStateStore stateStore, HighlightManager highlightManager) {
        if (source.getPlayer() == null) return 0;
        BlockPos pos = source.getPlayer().blockPosition();
        return setAnchor(source, stateStore, highlightManager, pos.getX(), pos.getZ());
    }

    private static int clearAnchor(FabricClientCommandSource source, ClientStateStore stateStore, HighlightManager highlightManager) {
        ClientSessionContext context = requireSessionContext(source);
        if (context == null) return 0;

        SessionState state = stateStore.get(context.environmentKey());
        SessionState updated = state.withAnchor(null);
        stateStore.set(context.environmentKey(), updated);
        highlightManager.clear();
        source.sendFeedback(Component.literal(PREFIX + "§7Anchor cleared."));
        return 1;
    }

    private static int showAnchor(FabricClientCommandSource source, ClientStateStore stateStore) {
        ClientSessionContext context = requireSessionContext(source);
        if (context == null) return 0;

        SessionState state = stateStore.get(context.environmentKey());
        AnchorState anchor = state.anchor();
        if (anchor == null) {
            source.sendFeedback(Component.literal(PREFIX + "§7No anchor saved."));
            return 1;
        }
        source.sendFeedback(Component.literal(PREFIX + "Anchor: X=" + anchor.pos().getX() + ", Z=" + anchor.pos().getZ() + " in " + anchor.dimensionId()));
        return 1;
    }

    private static int clearAll(FabricClientCommandSource source, ClientStateStore stateStore, HighlightManager highlightManager) {
        ClientSessionContext context = requireSessionContext(source);
        if (context == null) return 0;

        SessionState cleared = stateStore.get(context.environmentKey()).withLastCalculation(null);
        stateStore.set(context.environmentKey(), cleared);
        highlightManager.clear();
        source.sendFeedback(Component.literal(PREFIX + "§7All calculation data cleared."));
        return 1;
    }

    private static int autoDetectAnchor(FabricClientCommandSource source, ClientStateStore stateStore, HighlightManager highlightManager) {
        ClientSessionContext context = requireSessionContext(source);
        if (context == null) return 0;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return 0;

        SessionState state = stateStore.get(context.environmentKey());
        AutoAnchorDetector.DetectionResult res = AutoAnchorDetector.detectPigCannonAnchor(
            client.level, client.player.blockPosition()
        );

        if (res == null || res.pos() == null || res.pos().getY() <= 0) {
            source.sendFeedback(Component.literal(PREFIX + "§cFailed to find Pig Cannon anchor nearby! Stand near 4x9 selector panel, or set custom anchor via /cannon anchor set <x> <z>."));
            return 0;
        }

        BlockPos pos = res.pos();
        AnchorState anchor = new AnchorState(pos, context.dimensionId(), TemplateRegistry.CANNON_V1_DEFAULT, res.panelPos());
        SessionState updated = state.withAnchor(anchor);
        stateStore.set(context.environmentKey(), updated);
        highlightManager.restore(context, updated);
        source.sendFeedback(Component.literal(PREFIX + "§a✔ Auto-detected Pig Cannon Anchor at X=" + pos.getX() + ", Z=" + pos.getZ() + " (" + res.description() + ")"));
        return 1;
    }

    private static int toggleHud(FabricClientCommandSource source, CannonStatusHudManager hudManager) {
        if (hudManager == null) {
            source.sendFeedback(Component.literal(PREFIX + "§cHUD manager not available."));
            return 0;
        }
        boolean enabled = hudManager.toggleHud();
        source.sendFeedback(Component.literal(PREFIX + (enabled ? "§a✔ Action Bar HUD enabled." : "§cAction Bar HUD disabled.")));
        return 1;
    }

    private static int toggleHighlight(FabricClientCommandSource source, ClientStateStore stateStore, HighlightManager highlightManager) {
        ClientSessionContext context = requireSessionContext(source);
        if (context == null) return 0;

        SessionState current = stateStore.get(context.environmentKey());
        boolean newVisible = !current.highlightsVisible();
        SessionState updated = current.withHighlightsVisible(newVisible);
        stateStore.set(context.environmentKey(), updated);
        highlightManager.setVisible(newVisible);

        if (newVisible) {
            source.sendFeedback(Component.literal(PREFIX + "§aHighlights enabled."));
        } else {
            source.sendFeedback(Component.literal(PREFIX + "§7Highlights hidden."));
        }
        return 1;
    }

    private static int validateEncoding(FabricClientCommandSource source, ClientStateStore stateStore, TemplateRegistry templateRegistry) {
        ClientSessionContext context = requireSessionContext(source);
        if (context == null || source.getClient().level == null) return 0;

        SessionState state = stateStore.get(context.environmentKey());
        ValidationReport report = EncodingValidator.validate(source.getClient().level, templateRegistry, state);

        if (report.isValid()) {
            source.sendFeedback(Component.literal(PREFIX + "§a✔ Pig Cannon encoding verified! (" + report.totalChecked() + " blocks checked, 0 errors)"));
            if (source.getClient().player != null) {
                source.getClient().player.displayClientMessage(Component.literal("§a§l✔ ENCODING COMPLETE & READY TO FIRE!"), true);
            }
        } else {
            source.sendFeedback(Component.literal(PREFIX + "§cFound " + report.errors().size() + " encoding issue(s):"));
            for (EncodingError err : report.errors()) {
                String color = err.isError() ? "§c" : "§e";
                source.sendFeedback(Component.literal("  " + color + "• [" + err.pos().getX() + ", " + err.pos().getY() + ", " + err.pos().getZ() + "] " + err.message()));
            }
        }
        return 1;
    }

    private static ClientSessionContext requireSessionContext(FabricClientCommandSource source) {
        Optional<ClientSessionContext> context = ClientSessionContext.resolve(source.getClient());
        if (context.isEmpty()) {
            source.sendFeedback(Component.literal(PREFIX + "§cError: No active client session found."));
            return null;
        }
        return context.get();
    }
}
