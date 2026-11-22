package dev.selestor1462.cannonencoder.template;

import java.util.List;
import java.util.Objects;

public record HighlightPlan(
    String templateId,
    int directionCode,
    List<HighlightedBlock> blocks,
    int level,
    int column
) {
    public HighlightPlan {
        templateId = Objects.requireNonNull(templateId, "templateId");
        blocks = List.copyOf(blocks);
    }

    public HighlightPlan(String templateId, int directionCode, List<HighlightedBlock> blocks) {
        this(templateId, directionCode, blocks, 0, 0);
    }
}
