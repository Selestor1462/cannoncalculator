package dev.selestor1462.cannonencoder.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.selestor1462.cannonencoder.client.easyplace.EasyPlaceManager;
import dev.selestor1462.cannonencoder.client.gui.PigCannonScreen;
import dev.selestor1462.cannonencoder.client.validation.EncodingValidator;
import dev.selestor1462.cannonencoder.client.validation.ValidationReport;
import dev.selestor1462.cannonencoder.state.AnchorState;
import dev.selestor1462.cannonencoder.state.CannonMode;
import dev.selestor1462.cannonencoder.state.ClientStateStore;
import dev.selestor1462.cannonencoder.state.SessionState;
import dev.selestor1462.cannonencoder.template.TemplateRegistry;
import java.util.Optional;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public final class CannonCommand {
    private static final String PREFIX = "§6[Cannon] §r";

    private CannonCommand() {
    }

    public static void register(
        CommandDispatcher<FabricClientCommandSource> dispatcher,
        CommandBuildContext commandRegistryAccess,
        ClientStateStore stateStore,
        HighlightManager highlightManager,
        EasyPlaceManager easyPlaceManager,
        CannonStatusHudManager hudManager
    ) {
        LiteralArgumentBuilder<FabricClientCommandSource> node = ClientCommands.literal("cannon")
            // /cannon (no args) -> opens GUI
            .executes(context -> openGui(stateStore, highlightManager, easyPlaceManager))
            // /cannon gui
            .then(ClientCommands.literal("gui")
                .executes(context -> openGui(stateStore, highlightManager, easyPlaceManager)))
            // /cannon generator
            .then(ClientCommands.literal("generator")
                .executes(context -> openGeneratorGui(context.getSource()))
                .then(ClientCommands.literal("hud")
                    .executes(context -> toggleGeneratorHud(context.getSource())))
                .then(ClientCommands.literal("rescan")
                    .executes(context -> rescanGeneratorRoute(context.getSource())))
                .then(ClientCommands.literal("debug")
                    .executes(context -> toggleGeneratorDebug(context.getSource()))
                    .then(ClientCommands.literal("open")
                        .executes(context -> openGeneratorDebugGui(context.getSource()))
                    )
                )
            )
            // /cannon hud
            .then(ClientCommands.literal("hud")
                .executes(context -> toggleHud(context.getSource(), hudManager)))
            // /cannon easyplace
            .then(ClientCommands.literal("easyplace")
                .executes(context -> toggleEasyPlace(context.getSource(), stateStore, easyPlaceManager)))
            // /cannon rails (toggle sequential rails rendering)
            .then(ClientCommands.literal("rails")
                .executes(context -> toggleSequentialRails(context.getSource(), stateStore, highlightManager)))
            // /cannon toggle
            .then(ClientCommands.literal("toggle")
                .executes(context -> toggleHighlight(context.getSource(), stateStore, highlightManager)))
            // /cannon clear
            .then(ClientCommands.literal("clear")
                .executes(context -> clearHighlight(context.getSource(), stateStore, highlightManager)))
            // /cannon mode [pig|ftl]
            .then(ClientCommands.literal("mode")
                .then(ClientCommands.literal("pig")
                    .executes(context -> setMode(context.getSource(), stateStore, highlightManager, CannonMode.PIG_CANNON)))
                .then(ClientCommands.literal("ftl")
                    .executes(context -> setMode(context.getSource(), stateStore, highlightManager, CannonMode.FTL_PEARL_CANNON)))
            )
            // /cannon check
            .then(ClientCommands.literal("check")
                .executes(context -> checkEncoding(context.getSource(), stateStore, highlightManager)))
            // /cannon catcher (FTL catcher info)
            .then(ClientCommands.literal("catcher")
                .executes(context -> showCatcher(context.getSource(), stateStore)))
            // /cannon tp (teleport to active catcher block or landing pos)
            .then(ClientCommands.literal("tp")
                .executes(context -> tpCatcher(context.getSource(), stateStore)))
            // /cannon anchor ...
            .then(ClientCommands.literal("anchor")
                .then(ClientCommands.literal("auto")
                    .executes(context -> autoDetectAnchor(context.getSource(), stateStore, highlightManager)))
                .then(ClientCommands.literal("set")
                    .then(ClientCommands.literal("here")
                        .executes(context -> setAnchorHere(context.getSource(), stateStore, highlightManager)))
                    .then(ClientCommands.argument("x", IntegerArgumentType.integer())
                        .then(ClientCommands.argument("z", IntegerArgumentType.integer())
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
                .then(ClientCommands.literal("clear")
                    .executes(context -> clearAnchor(context.getSource(), stateStore, highlightManager)))
                .then(ClientCommands.literal("show")
                    .executes(context -> showAnchor(context.getSource(), stateStore)))
            );

        dispatcher.register(node);
    }

    private static int openGui(ClientStateStore stateStore, HighlightManager highlightManager, EasyPlaceManager easyPlaceManager) {
        PigCannonEncoderClient.setScreenNextTick(() -> new PigCannonScreen(stateStore, highlightManager, easyPlaceManager));
        return 1;
    }

    private static int toggleSequentialRails(FabricClientCommandSource source, ClientStateStore stateStore, HighlightManager highlightManager) {
        if (highlightManager == null) {
            source.sendFeedback(Component.literal(PREFIX + "§cHighlight manager not available."));
            return 0;
        }
        boolean enabled = highlightManager.toggleSequentialRails();
        ClientSessionContext context = ClientSessionContext.resolve(Minecraft.getInstance()).orElse(null);
        if (context != null && stateStore != null) {
            SessionState state = stateStore.get(context.environmentKey()).withSequentialRails(enabled);
            stateStore.set(context.environmentKey(), state);
        }
        if (enabled) {
            source.sendFeedback(Component.literal(PREFIX + "Sequential Rails: §aENABLED §7(ghost rails only render after placing supporting block)."));
        } else {
            source.sendFeedback(Component.literal(PREFIX + "Sequential Rails: §cDISABLED §7(all ghost rails render immediately)."));
        }
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

    private static int setMode(FabricClientCommandSource source, ClientStateStore stateStore, HighlightManager highlightManager, CannonMode mode) {
        ClientSessionContext context = requireSessionContext(source);
        if (context == null) return 0;

        SessionState state = stateStore.get(context.environmentKey());
        SessionState updated = state.withMode(mode);
        stateStore.set(context.environmentKey(), updated);
        highlightManager.restore(context, updated);

        source.sendFeedback(Component.literal(PREFIX + "§aSwitched active cannon mode to: §f" + mode.displayName()));
        return 1;
    }

    private static int setAnchor(FabricClientCommandSource source, ClientStateStore stateStore, HighlightManager highlightManager, int x, int z) {
        ClientSessionContext context = requireSessionContext(source);
        if (context == null) return 0;

        SessionState state = stateStore.get(context.environmentKey());
        BlockPos pos = new BlockPos(x, 0, z);

        if (state.cannonMode() == CannonMode.FTL_PEARL_CANNON) {
            AnchorState anchor = new AnchorState(pos, context.dimensionId(), TemplateRegistry.FTL_836_DEFAULT);
            SessionState updated = state.withFtlAnchor(anchor);
            stateStore.set(context.environmentKey(), updated);
            source.sendFeedback(Component.literal(PREFIX + "§aFTL Anchor (Pearl Origin) saved at X=" + x + ", Z=" + z));
            if (updated.ftlCalculation() != null) {
                highlightManager.applyFtl(context, updated, updated.ftlCalculation(), updated.ftlSelectedRow(), updated.ftlSelectedCol());
            }
        } else {
            AnchorState anchor = new AnchorState(pos, context.dimensionId(), TemplateRegistry.CANNON_V1_DEFAULT);
            SessionState updated = state.withAnchor(anchor);
            stateStore.set(context.environmentKey(), updated);
            source.sendFeedback(Component.literal(PREFIX + "§aPig Cannon Anchor saved at X=" + x + ", Z=" + z));
            if (updated.lastCalculation() != null) {
                highlightManager.apply(context, updated, updated.lastCalculation(), updated.selectedRow(), updated.selectedCol());
            }
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
        SessionState updated = (state.cannonMode() == CannonMode.FTL_PEARL_CANNON)
            ? state.withFtlAnchor(null)
            : state.withAnchor(null);
        stateStore.set(context.environmentKey(), updated);
        highlightManager.clear();
        source.sendFeedback(Component.literal(PREFIX + "§7Anchor cleared for " + state.cannonMode().displayName() + "."));
        return 1;
    }

    private static int showAnchor(FabricClientCommandSource source, ClientStateStore stateStore) {
        ClientSessionContext context = requireSessionContext(source);
        if (context == null) return 0;

        SessionState state = stateStore.get(context.environmentKey());
        AnchorState anchor = state.effectiveAnchor(state.cannonMode());
        if (anchor == null) {
            source.sendFeedback(Component.literal(PREFIX + "§7No anchor saved for " + state.cannonMode().displayName() + "."));
            return 0;
        }
        source.sendFeedback(Component.literal(PREFIX + state.cannonMode().displayName() + " Anchor: X=" + anchor.pos().getX() + ", Z=" + anchor.pos().getZ() + " in " + anchor.dimensionId()));
        return 1;
    }

    private static int tpCatcher(FabricClientCommandSource source, ClientStateStore stateStore) {
        ClientSessionContext context = requireSessionContext(source);
        if (context == null) return 0;

        SessionState state = stateStore.get(context.environmentKey());
        BlockPos targetPos = null;
        if (state.cannonMode() == CannonMode.FTL_PEARL_CANNON) {
            targetPos = state.activeCatcherPos();
        } else if (state.lastCalculation() != null) {
            targetPos = new BlockPos(state.lastCalculation().landingX(), 256, state.lastCalculation().landingZ());
        }

        if (targetPos == null) {
            source.sendFeedback(Component.literal(PREFIX + "§eNo active catcher/destination calculated."));
            return 0;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.player.connection != null) {
            client.player.connection.sendCommand(
                String.format(java.util.Locale.ROOT, "tp %d %d %d", targetPos.getX(), targetPos.getY(), targetPos.getZ())
            );
            source.sendFeedback(Component.literal(PREFIX + "§aTeleporting to " + targetPos.toShortString() + "..."));
            return 1;
        }
        return 0;
    }

    private static int checkEncoding(FabricClientCommandSource source, ClientStateStore stateStore, HighlightManager highlightManager) {
        Minecraft client = Minecraft.getInstance();
        ClientSessionContext context = requireSessionContext(source);
        if (context == null || client.level == null) return 0;

        SessionState state = stateStore.get(context.environmentKey());
        ValidationReport report;
        if (state.cannonMode() == CannonMode.FTL_PEARL_CANNON) {
            report = EncodingValidator.validateFtl(client.level, highlightManager.templateRegistry(), state);
        } else {
            report = EncodingValidator.validate(client.level, highlightManager.templateRegistry(), state);
        }

        for (Component msg : report.formatMessages()) {
            source.sendFeedback(msg);
        }
        if (report.isValid() && client.player != null) {
            client.player.sendSystemMessage(Component.literal("§a§l✔ ENCODING COMPLETE & READY TO FIRE!"));
        }
        return report.isValid() ? 1 : 0;
    }

    private static int showCatcher(FabricClientCommandSource source, ClientStateStore stateStore) {
        ClientSessionContext context = requireSessionContext(source);
        if (context == null) return 0;

        SessionState state = stateStore.get(context.environmentKey());
        BlockPos catcher = state.activeCatcherPos();
        if (catcher == null) {
            source.sendFeedback(Component.literal(PREFIX + "§eNo active catcher block calculated. Open GUI to calculate."));
            return 0;
        }

        double dist = (source.getPlayer() != null) ? Math.sqrt(source.getPlayer().distanceToSqr(catcher.getX(), catcher.getY(), catcher.getZ())) : 0.0;
        source.sendFeedback(Component.literal(PREFIX + "§6Catcher Block at: §e" + catcher.toShortString() + " §7(Distance: " + String.format("%.1f", dist) + "m)"));
        source.sendFeedback(Component.literal("§7Hold a block and right click near the ghost to place it!"));
        return 1;
    }

    private static int toggleHud(FabricClientCommandSource source, CannonStatusHudManager hudManager) {
        if (hudManager == null) return 0;
        boolean enabled = hudManager.toggleHud();
        source.sendFeedback(Component.literal(PREFIX + (enabled ? "§aAction Bar Status HUD enabled." : "§cAction Bar Status HUD disabled.")));
        return 1;
    }

    private static int autoDetectAnchor(FabricClientCommandSource source, ClientStateStore stateStore, HighlightManager highlightManager) {
        ClientSessionContext context = requireSessionContext(source);
        if (context == null) return 0;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return 0;

        SessionState state = stateStore.get(context.environmentKey());
        AutoAnchorDetector.DetectionResult res = AutoAnchorDetector.detectAnchor(
            client.level, client.player.blockPosition(), state.cannonMode()
        );

        if (res == null || res.pos() == null || res.pos().getY() <= 0) {
            String structureTip = (state.cannonMode() == CannonMode.FTL_PEARL_CANNON)
                ? "firing lever"
                : "4x9 selector panel";
            source.sendFeedback(Component.literal(PREFIX + "§cFailed to find " + state.cannonMode().displayName() + " anchor nearby! Stand near " + structureTip + ", or set custom anchor via /cannon anchor set <x> <z>."));
            return 0;
        }

        BlockPos pos = res.pos();
        BlockPos panelPos = res.panelPos();
        if (state.cannonMode() == CannonMode.FTL_PEARL_CANNON) {
            AnchorState anchor = new AnchorState(pos, context.dimensionId(), TemplateRegistry.FTL_836_DEFAULT, panelPos);
            SessionState updated = state.withFtlAnchor(anchor);
            stateStore.set(context.environmentKey(), updated);
            highlightManager.restore(context, updated);
            source.sendFeedback(Component.literal(PREFIX + "§a✔ Auto-detected FTL Anchor at X=" + pos.getX() + ", Z=" + pos.getZ() + " (" + res.description() + ")"));
        } else {
            AnchorState anchor = new AnchorState(pos, context.dimensionId(), TemplateRegistry.CANNON_V1_DEFAULT, panelPos);
            SessionState updated = state.withAnchor(anchor);
            stateStore.set(context.environmentKey(), updated);
            highlightManager.restore(context, updated);
            source.sendFeedback(Component.literal(PREFIX + "§a✔ Auto-detected Pig Cannon Anchor at X=" + pos.getX() + ", Z=" + pos.getZ() + " (" + res.description() + ")"));
        }
        return 1;
    }

    private static int toggleHighlight(FabricClientCommandSource source, ClientStateStore stateStore, HighlightManager highlightManager) {
        ClientSessionContext context = requireSessionContext(source);
        if (context == null) return 0;
        SessionState state = stateStore.get(context.environmentKey());
        boolean newVis = !state.highlightsVisible();
        highlightManager.setVisible(newVis);
        stateStore.set(context.environmentKey(), state.withHighlightsVisible(newVis));
        source.sendFeedback(Component.literal(PREFIX + (newVis ? "§aWorld hologram glowing ENABLED." : "§7World hologram glowing HIDDEN.")));
        return 1;
    }

    private static int clearHighlight(FabricClientCommandSource source, ClientStateStore stateStore, HighlightManager highlightManager) {
        highlightManager.clear();
        ClientSessionContext context = requireSessionContext(source);
        if (context != null) {
            SessionState state = stateStore.get(context.environmentKey());
            stateStore.set(context.environmentKey(), state.withLastCalculation(null).withFtlCalculation(null));
        }
        source.sendFeedback(Component.literal(PREFIX + "§7Active highlights cleared."));
        return 1;
    }

    private static ClientSessionContext requireSessionContext(FabricClientCommandSource source) {
        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(Minecraft.getInstance());
        if (sessionContext.isEmpty()) {
            source.sendFeedback(Component.literal(PREFIX + "§cWorld context is not available."));
            return null;
        }
        return sessionContext.get();
    }

    private static int openGeneratorGui(FabricClientCommandSource source) {
        fi.dy.masa.litematica.selection.AreaSelection selection = null;
        try {
            selection = fi.dy.masa.litematica.data.DataManager.getSelectionManager().getCurrentSelection();
        } catch (Throwable ignored) {}
        final fi.dy.masa.litematica.selection.AreaSelection finalSelection = selection;
        PigCannonEncoderClient.setScreenNextTick(() -> new dev.selestor1462.cannonencoder.client.gui.GuiReturnCannon(finalSelection, null));
        return 1;
    }

    private static int toggleGeneratorHud(FabricClientCommandSource source) {
        dev.selestor1462.cannonencoder.client.util.ChunkFlightRouteRenderer.toggleHud();
        dev.selestor1462.cannonencoder.client.gui.GuiReturnCannon.setLastShowRouteHud(dev.selestor1462.cannonencoder.client.util.ChunkFlightRouteRenderer.isHudEnabled());
        String state = dev.selestor1462.cannonencoder.client.util.ChunkFlightRouteRenderer.isHudEnabled() ? "§aVISIBLE" : "§cCLOAKED/HIDDEN";
        source.sendFeedback(Component.literal(PREFIX + "§fRoute scan HUD is now " + state + "."));
        return 1;
    }

    private static int rescanGeneratorRoute(FabricClientCommandSource source) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null) {
            dev.selestor1462.cannonencoder.client.util.ChunkFlightRouteRenderer.restartScan(client.level);
            dev.selestor1462.cannonencoder.client.util.ChunkFlightRouteRenderer.setHudEnabled(true);
            dev.selestor1462.cannonencoder.client.gui.GuiReturnCannon.setLastShowRouteHud(true);

            int totalChunks = dev.selestor1462.cannonencoder.cannon.PearlFlightSimulator.getTotalCandidateChunksCount();
            int loadedChunks = dev.selestor1462.cannonencoder.cannon.PearlFlightSimulator.getVerifiedCandidateChunksCount(client.level);
            int remaining = Math.max(0, totalChunks - loadedChunks);
            int colCount = dev.selestor1462.cannonencoder.cannon.PearlFlightSimulator.getCollidingRoutesCount();

            if (totalChunks == 0) {
                source.sendFeedback(Component.literal(PREFIX + "§eRoute scan restarted. No active cannon solutions loaded."));
            } else if (remaining > 0) {
                String colStr = (colCount > 0)
                        ? String.format(java.util.Locale.ROOT, " §c⚠ %d collision%s detected in loaded chunks!", colCount, colCount == 1 ? "" : "s")
                        : " §eNo collisions in loaded chunks.";
                source.sendFeedback(Component.literal(String.format(java.util.Locale.ROOT,
                        PREFIX + "§fRoute scan started! Checked §e%d / %d §fchunks (§e%d §fremaining).%s",
                        loadedChunks, totalChunks, remaining, colStr)));
            } else {
                String colStr = (colCount == 0)
                        ? "§a0 collisions detected (all clear)!"
                        : String.format(java.util.Locale.ROOT, "§c⚠ %d collision%s detected!", colCount, colCount == 1 ? "" : "s");
                source.sendFeedback(Component.literal(String.format(java.util.Locale.ROOT,
                        PREFIX + "§fRoute scan complete! All §a%d §fchunks verified. %s",
                        totalChunks, colStr)));
            }
        }
        return 1;
    }

    private static int toggleGeneratorDebug(FabricClientCommandSource source) {
        dev.selestor1462.cannonencoder.client.gui.GuiReturnCannonDebug.debugModeEnabled = !dev.selestor1462.cannonencoder.client.gui.GuiReturnCannonDebug.debugModeEnabled;
        String state = dev.selestor1462.cannonencoder.client.gui.GuiReturnCannonDebug.debugModeEnabled ? "§aENABLED" : "§cDISABLED";
        source.sendFeedback(Component.literal(PREFIX + "§fReturn Cannon debug menu is now " + state + "."));
        return 1;
    }

    private static int openGeneratorDebugGui(FabricClientCommandSource source) {
        dev.selestor1462.cannonencoder.client.gui.GuiReturnCannonDebug.debugModeEnabled = true;
        java.nio.file.Path gameDir = java.nio.file.Path.of(".");
        try {
            java.nio.file.Path schemDir = fi.dy.masa.litematica.data.DataManager.getSchematicsBaseDirectory();
            if (schemDir != null && schemDir.getParent() != null) {
                gameDir = schemDir.getParent();
            }
        } catch (Throwable ignored) {}
        final java.nio.file.Path finalGameDir = gameDir;
        PigCannonEncoderClient.setScreenNextTick(() -> new dev.selestor1462.cannonencoder.client.gui.GuiReturnCannonDebug(null, dev.selestor1462.cannonencoder.cannon.PearlCalculator.loadSettings(finalGameDir)));
        return 1;
    }
}
