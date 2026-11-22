package dev.selestor1462.cannonencoder.template;

import dev.selestor1462.cannonencoder.calc.CalculationResult;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;

public record PigCannonTemplate(String id, Map<Integer, DirectionTemplate> directions) {
    public PigCannonTemplate {
        id = Objects.requireNonNull(id, "id");
        directions = Map.copyOf(directions);
    }

    public Optional<HighlightPlan> resolve(BlockPos anchor, CalculationResult result) {
        return this.resolve(anchor, result, 0, 0);
    }

    public Optional<HighlightPlan> resolve(BlockPos anchor, CalculationResult result, int level, int column) {
        return this.resolve(anchor, result, level, column, null);
    }

    public Optional<HighlightPlan> resolve(BlockPos anchor, CalculationResult result, int level, int column, BlockPos knownPanelPos) {
        int dirCode = (result != null) ? result.directionCode() : 0;
        DirectionTemplate directionTemplate = this.directions.get(dirCode);
        if (directionTemplate == null && !this.directions.isEmpty()) {
            directionTemplate = this.directions.values().iterator().next();
        }
        if (directionTemplate == null) {
            PigCannonTemplate defaultTmpl = TemplateRegistry.createDefault().getTemplate(TemplateRegistry.CANNON_V1_DEFAULT);
            if (defaultTmpl != null && !defaultTmpl.directions().isEmpty()) {
                directionTemplate = defaultTmpl.directions().get(dirCode);
                if (directionTemplate == null) {
                    directionTemplate = defaultTmpl.directions().values().iterator().next();
                }
            }
        }
        if (directionTemplate == null) {
            return Optional.empty();
        }
        return directionTemplate.resolve(this.id, anchor, result, level, column, knownPanelPos);
    }
}
