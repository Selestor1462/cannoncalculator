package dev.selestor1462.cannonencoder.client;

import dev.selestor1462.cannonencoder.client.gui.PigCannonScreen;
import dev.selestor1462.cannonencoder.state.ClientStateStore;
import dev.selestor1462.cannonencoder.template.TemplateRegistry;
import java.util.Optional;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;

public final class PigCannonEncoderClient implements ClientModInitializer {
    private final ClientStateStore stateStore = ClientStateStore.openDefault();
    private final HighlightManager highlightManager = new HighlightManager(TemplateRegistry.createDefault());
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
        this.easyPlaceManager.setEnabled(this.stateStore.globalEasyPlace());

        // Dimension change tracking & deferred GUI opening
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (pendingScreenSupplier != null) {
                java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> supplier = pendingScreenSupplier;
                pendingScreenSupplier = null;
                try {
                    client.setScreen(supplier.get());
                } catch (Throwable t) {
                    dev.selestor1462.cannonencoder.PigCannonEncoder.LOGGER.error("Failed to open Cannon Screen", t);
                    if (client.player != null) {
                        client.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[Cannon] Failed to open GUI: " + t.getMessage()), false);
                    }
                }
            }

            if (client.level != null) {
                String currentDim = client.level.dimension().location().toString();
                Optional<ClientSessionContext> sessionOpt = ClientSessionContext.resolve(client);
                String currentEnv = sessionOpt.map(ClientSessionContext::environmentKey).orElse(null);
                if (!java.util.Objects.equals(currentDim, this.lastDimensionId) || !java.util.Objects.equals(currentEnv, this.lastEnvironmentKey)) {
                    this.lastDimensionId = currentDim;
                    this.lastEnvironmentKey = currentEnv;
                    this.pendingChunkRestore = true;
                    this.restoreHighlight(client);
                }

                if (this.pendingChunkRestore && sessionOpt.isPresent()) {
                    dev.selestor1462.cannonencoder.state.SessionState s = this.stateStore.get(sessionOpt.get().environmentKey());
                    dev.selestor1462.cannonencoder.state.AnchorState a = s.anchor();
                    if (a != null && client.level.hasChunkAt(a.pos())) {
                        this.pendingChunkRestore = false;
                        this.restoreHighlight(client);
                    }
                }

                this.hudManager.onTick(client);
            }
        });

        // Register event callbacks
        this.pickerManager.register();
        this.easyPlaceManager.register();

        // Register unified client command (/cannon)
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            CannonCommand.register(dispatcher, registryAccess, this.stateStore, this.highlightManager, this.easyPlaceManager, this.hudManager);
        });

        // Session join & disconnect
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            this.pendingChunkRestore = true;
            this.restoreHighlight(client);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            this.lastDimensionId = null;
            this.lastEnvironmentKey = null;
            this.pendingChunkRestore = false;
            this.highlightManager.clear();
        });

        // World rendering: 3D models and wireframes
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            WorldHighlightRenderer.renderBlockModels(context, this.highlightManager);
            WorldHighlightRenderer.render(context, this.highlightManager);
        });
    }

    private void restoreHighlight(Minecraft client) {
        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(client);
        if (sessionContext.isEmpty()) {
            this.highlightManager.clear();
            return;
        }
        ClientSessionContext context = sessionContext.get();
        dev.selestor1462.cannonencoder.state.SessionState state = this.stateStore.get(context.environmentKey());

        // If chunks are loaded in the world, verify and persist panelPos if not yet recorded
        if (client.level != null && state.anchor() != null) {
            dev.selestor1462.cannonencoder.state.AnchorState pa = state.anchor();
            if (pa.panelPos() == null && client.level.hasChunkAt(pa.pos())) {
                net.minecraft.core.BlockPos p = dev.selestor1462.cannonencoder.template.DirectionTemplate.findPigCannonLampPanel(client.level, pa.pos());
                if (p != null) {
                    dev.selestor1462.cannonencoder.state.AnchorState updatedPa = pa.withPanelPos(p);
                    state = state.withAnchor(updatedPa);
                    this.stateStore.set(context.environmentKey(), state);
                }
            }
        }

        this.easyPlaceManager.setEnabled(state.easyPlaceEnabled());
        this.highlightManager.restore(context, state);
    }
}
