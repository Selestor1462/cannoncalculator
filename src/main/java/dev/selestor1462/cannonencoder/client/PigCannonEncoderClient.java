package dev.selestor1462.cannonencoder.client;

import dev.selestor1462.cannonencoder.client.catcher.CatcherPlacementManager;
import dev.selestor1462.cannonencoder.client.gui.PigCannonScreen;
import dev.selestor1462.cannonencoder.state.AnchorState;
import dev.selestor1462.cannonencoder.state.CannonMode;
import dev.selestor1462.cannonencoder.state.ClientStateStore;
import dev.selestor1462.cannonencoder.state.SessionState;
import dev.selestor1462.cannonencoder.template.TemplateRegistry;
import java.util.Optional;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class PigCannonEncoderClient implements ClientModInitializer {
    private final ClientStateStore stateStore = ClientStateStore.openDefault();
    private final HighlightManager highlightManager = new HighlightManager(TemplateRegistry.createDefault());
    private final CatcherPlacementManager catcherManager = new CatcherPlacementManager(this.stateStore);
    private final dev.selestor1462.cannonencoder.client.picker.BlockPickerManager pickerManager =
        new dev.selestor1462.cannonencoder.client.picker.BlockPickerManager(this.highlightManager);
    private final dev.selestor1462.cannonencoder.client.easyplace.EasyPlaceManager easyPlaceManager =
        new dev.selestor1462.cannonencoder.client.easyplace.EasyPlaceManager(this.highlightManager, this.pickerManager);
    private final CannonStatusHudManager hudManager =
        new CannonStatusHudManager(this.stateStore, this.highlightManager, this.easyPlaceManager);

    private String lastDimensionId = null;
    private String lastEnvironmentKey = null;
    private boolean pendingChunkRestore = false;

    private static java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> pendingScreenSupplier = null;

    public static void setScreenNextTick(java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> screenSupplier) {
        pendingScreenSupplier = screenSupplier;
    }

    @Override
    public void onInitializeClient() {
        dev.selestor1462.cannonencoder.client.util.ChunkFlightRouteRenderer.init();
        dev.selestor1462.cannonencoder.client.util.PearlTrajectoryLineRenderer.init();

        // Dimension change tracking & deferred GUI opening
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (pendingScreenSupplier != null) {
                java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> supplier = pendingScreenSupplier;
                pendingScreenSupplier = null;
                try {
                    client.gui.setScreen(supplier.get());
                } catch (Throwable t) {
                    dev.selestor1462.cannonencoder.PigCannonEncoder.LOGGER.error("Failed to open Cannon Screen", t);
                    if (client.player != null) {
                        client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[Cannon] Failed to open GUI: " + t.getMessage()));
                    }
                }
            }

            if (client.level != null) {
                String currentDim = client.level.dimension().identifier().toString();
                Optional<ClientSessionContext> sessionOpt = ClientSessionContext.resolve(client);
                String currentEnv = sessionOpt.map(ClientSessionContext::environmentKey).orElse(null);
                if (!java.util.Objects.equals(currentDim, this.lastDimensionId) || !java.util.Objects.equals(currentEnv, this.lastEnvironmentKey)) {
                    this.lastDimensionId = currentDim;
                    this.lastEnvironmentKey = currentEnv;
                    this.pendingChunkRestore = true;
                    this.restoreSession(client);
                }

                if (this.pendingChunkRestore && sessionOpt.isPresent()) {
                    SessionState s = this.stateStore.get(sessionOpt.get().environmentKey());
                    AnchorState a = s.effectiveAnchor(s.cannonMode());
                    if (a != null && client.level.hasChunkAt(a.pos())) {
                        this.pendingChunkRestore = false;
                        this.restoreSession(client);
                    }
                }

                this.hudManager.onTick(client);
            }
        });

        // Initialize defaults from persistent store
        this.easyPlaceManager.setEnabled(this.stateStore.globalEasyPlace());

        // Register event callbacks
        this.catcherManager.register();
        this.pickerManager.register();
        this.easyPlaceManager.register();

        // Register unified client command (/cannon)
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            CannonCommand.register(dispatcher, registryAccess, this.stateStore, this.highlightManager, this.easyPlaceManager, this.hudManager);
        });

        // Session join & disconnect
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            this.pendingChunkRestore = true;
            this.restoreSession(client);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            this.lastDimensionId = null;
            this.lastEnvironmentKey = null;
            this.pendingChunkRestore = false;
            this.highlightManager.clear();
        });

        // Block model rendering (renders real 3D textured block models for observer, rails, dust, glass, etc.)
        LevelRenderEvents.COLLECT_SUBMITS.register(context ->
            WorldHighlightRenderer.renderBlockModels(context, this.highlightManager)
        );

        // World Gizmo rendering (borders, direction arrows, beacon beams)
        LevelRenderEvents.BEFORE_GIZMOS.register(context ->
            WorldHighlightRenderer.render(context, this.highlightManager)
        );
    }

    private void restoreSession(Minecraft client) {
        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(client);
        if (sessionContext.isEmpty()) {
            this.highlightManager.clear();
            return;
        }
        SessionState state = this.stateStore.get(sessionContext.get().environmentKey());

        // If chunks are loaded in the world, verify and persist panelPos if not yet recorded
        if (client.level != null) {
            if (state.cannonMode() == CannonMode.FTL_PEARL_CANNON && state.ftlAnchor() != null) {
                AnchorState fa = state.ftlAnchor();
                if (fa.panelPos() == null && client.level.hasChunkAt(fa.pos())) {
                    BlockPos p = dev.selestor1462.cannonencoder.template.FtlPearlCannonTemplate.findFtlLampPanel(fa.pos());
                    if (p != null) {
                        AnchorState updatedFa = fa.withPanelPos(p);
                        state = state.withFtlAnchor(updatedFa);
                        this.stateStore.set(sessionContext.get().environmentKey(), state);
                    }
                }
            } else if (state.cannonMode() == CannonMode.PIG_CANNON && state.anchor() != null) {
                AnchorState pa = state.anchor();
                if (pa.panelPos() == null && client.level.hasChunkAt(pa.pos())) {
                    BlockPos p = dev.selestor1462.cannonencoder.template.DirectionTemplate.findPigCannonLampPanel(client.level, pa.pos());
                    if (p != null) {
                        AnchorState updatedPa = pa.withPanelPos(p);
                        state = state.withAnchor(updatedPa);
                        this.stateStore.set(sessionContext.get().environmentKey(), state);
                    }
                }
            }
        }

        this.highlightManager.restore(sessionContext.get(), state);
        this.easyPlaceManager.setEnabled(state.easyPlaceEnabled());
    }
}
