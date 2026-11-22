package dev.selestor1462.cannonencoder.client;

import dev.selestor1462.cannonencoder.client.easyplace.EasyPlaceManager;
import dev.selestor1462.cannonencoder.state.CannonMode;
import dev.selestor1462.cannonencoder.state.ClientStateStore;
import dev.selestor1462.cannonencoder.state.SessionState;
import dev.selestor1462.cannonencoder.template.HighlightedBlock;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class CannonStatusHudManager {
    private final ClientStateStore stateStore;
    private final HighlightManager highlightManager;
    private final EasyPlaceManager easyPlaceManager;

    private int hudUpdateTick = 0;
    private boolean hudEnabled = false;

    public boolean isHudEnabled() {
        return this.hudEnabled;
    }

    public void setHudEnabled(boolean enabled) {
        this.hudEnabled = enabled;
    }

    public boolean toggleHud() {
        this.hudEnabled = !this.hudEnabled;
        return this.hudEnabled;
    }

    public CannonStatusHudManager(
        ClientStateStore stateStore,
        HighlightManager highlightManager,
        EasyPlaceManager easyPlaceManager
    ) {
        this.stateStore = stateStore;
        this.highlightManager = highlightManager;
        this.easyPlaceManager = easyPlaceManager;
    }

    public void onTick(Minecraft client) {
        if (client == null || client.player == null || client.level == null) {
            return;
        }

        Optional<ClientSessionContext> session = ClientSessionContext.resolve(client);
        if (session.isEmpty()) {
            return;
        }

        List<HighlightedBlock> blocks = this.highlightManager.visibleHighlights(session.get());
        if (blocks.isEmpty()) {
            return;
        }

        int totalPlaceable = 0;
        int satisfiedCount = 0;

        for (HighlightedBlock hb : blocks) {
            if (EasyPlaceManager.isPlaceableHighlight(hb.type())) {
                totalPlaceable++;
                if (WorldHighlightRenderer.isBlockSatisfied(client.level, hb)) {
                    satisfiedCount++;
                }
            }
        }

        // Compact status HUD on Action Bar when encoding is active (disabled by default)
        this.hudUpdateTick++;
        if (this.hudEnabled && this.hudUpdateTick % 20 == 0 && totalPlaceable > 0) {
            SessionState state = this.stateStore.get(session.get().environmentKey());
            String targetDesc = "";
            if (state.lastCalculation() != null) {
                targetDesc = String.format(
                    java.util.Locale.ROOT,
                    "§dPig: §fX=%d, Z=%d §7(L%d C%d)",
                    state.lastCalculation().destX(),
                    state.lastCalculation().destZ(),
                    state.selectedRow(),
                    state.selectedCol() + 1
                );
            }

            int pct = (satisfiedCount * 100) / totalPlaceable;
            String easyPill = (this.easyPlaceManager != null && this.easyPlaceManager.isEnabled()) ? "§a⚡Easy" : "§7⚡Off";

            client.player.displayClientMessage(Component.literal(
                targetDesc + " §7| §e" + satisfiedCount + "/" + totalPlaceable + " (" + pct + "%) §7| " + easyPill
            ), true);
        }
    }
}
