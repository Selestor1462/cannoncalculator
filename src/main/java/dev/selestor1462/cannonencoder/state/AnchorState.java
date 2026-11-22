package dev.selestor1462.cannonencoder.state;

import java.util.Objects;
import net.minecraft.core.BlockPos;

public record AnchorState(BlockPos pos, String dimensionId, String templateId, BlockPos panelPos) {
    public AnchorState {
        pos = Objects.requireNonNull(pos, "pos");
        dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
        templateId = Objects.requireNonNull(templateId, "templateId");
    }

    public AnchorState(BlockPos pos, String dimensionId, String templateId) {
        this(pos, dimensionId, templateId, null);
    }

    public AnchorState withPanelPos(BlockPos newPanelPos) {
        return new AnchorState(this.pos, this.dimensionId, this.templateId, newPanelPos);
    }
}
