package dev.selestor1462.cannonencoder.template;

import dev.selestor1462.cannonencoder.calc.CalculationResult;
import dev.selestor1462.cannonencoder.calc.CodingSlot;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class TemplateRegistry {
    public static final String UNCONFIGURED_TEMPLATE_ID = "unconfigured";
    public static final String CANNON_V1 = "cannon_v1";
    public static final String CANNON_V1_ROT0 = "cannon_v1_rot0";
    public static final String CANNON_V1_DEFAULT = "cannon_v1";

    private final Map<String, PigCannonTemplate> templates;

    public TemplateRegistry(Map<String, PigCannonTemplate> templates) {
        this.templates = Map.copyOf(templates);
    }

    public static TemplateRegistry createDefault() {
        PigCannonTemplate canonicalPig = buildCannonV1(CANNON_V1_DEFAULT);
        Map<String, PigCannonTemplate> pigMap = Map.of(
            UNCONFIGURED_TEMPLATE_ID, new PigCannonTemplate(UNCONFIGURED_TEMPLATE_ID, Map.of()),
            CANNON_V1_DEFAULT, canonicalPig,
            CANNON_V1_ROT0, canonicalPig
        );

        return new TemplateRegistry(pigMap);
    }

    private static PigCannonTemplate buildCannonV1(String id) {
        int[][] base = new int[][]{
            {15, -3, -16, 0, -1, 3, 0},   // DIRECTION (Z=295..297 in schematic)
            {15, -3, -19, 0, -1, 6, 3},   // FOURTH_ACCEL (Z=289..294 in schematic)
            {15, -3, -25, 0, -1, 17, 9},  // THIRD_ACCEL (Z=272..288 in schematic)
            {15, -3, -42, 0, -1, 6, 26},  // SECOND_ACCEL (Z=266..271 in schematic)
            {15, -3, -48, 0, -1, 17, 32}  // FIRST_ACCEL (Z=249..265 in schematic)
        };

        CodingSlot[] slots = new CodingSlot[]{
            CodingSlot.DIRECTION,
            CodingSlot.FOURTH_ACCEL,
            CodingSlot.THIRD_ACCEL,
            CodingSlot.SECOND_ACCEL,
            CodingSlot.FIRST_ACCEL
        };

        RelativeBlockPos bridgeVec = new RelativeBlockPos(1, 0, 0);

        EnumMap<CodingSlot, SlotPattern> patterns = new EnumMap<>(CodingSlot.class);
        for (int i = 0; i < slots.length; ++i) {
            int[] r = base[i];
            patterns.put(slots[i], new SlotPattern(
                slots[i],
                new RelativeBlockPos(r[0], r[1], r[2]),
                new RelativeBlockPos(r[3], 0, r[4]),
                bridgeVec,
                Direction.WEST,
                r[5],
                r[6]
            ));
        }

        HashMap<Integer, DirectionTemplate> directions = new HashMap<>();
        for (int code = 0; code < 8; ++code) {
            directions.put(code, new DirectionTemplate(code, Map.copyOf(patterns), 0));
        }

        return new PigCannonTemplate(id, directions);
    }

    public Optional<HighlightPlan> resolve(String templateId, BlockPos anchor, CalculationResult result) {
        return this.resolve(templateId, anchor, result, 0, 0);
    }

    public Optional<HighlightPlan> resolve(dev.selestor1462.cannonencoder.state.AnchorState anchor, CalculationResult result, int level, int column) {
        Objects.requireNonNull(anchor, "anchor");
        PigCannonTemplate template = getTemplate(anchor.templateId());
        if (template == null) {
            return Optional.empty();
        }
        return template.resolve(anchor.pos(), result, level, column, anchor.panelPos());
    }

    public Optional<HighlightPlan> resolve(String templateId, BlockPos anchor, CalculationResult result, int level, int column) {
        Objects.requireNonNull(anchor, "anchor");
        PigCannonTemplate template = getTemplate(templateId);
        if (template == null) {
            return Optional.empty();
        }
        return template.resolve(anchor, result, level, column, null);
    }

    public PigCannonTemplate getTemplate(String templateId) {
        if (templateId != null) {
            PigCannonTemplate tmpl = this.templates.get(templateId);
            if (tmpl != null) return tmpl;
            String normalized = templateId.replace("_rot0", "").replace("_rot90", "").replace("_rot180", "").replace("_rot270", "");
            tmpl = this.templates.get(normalized);
            if (tmpl != null) return tmpl;
        }
        PigCannonTemplate fallback = this.templates.get(CANNON_V1_DEFAULT);
        if (fallback != null) return fallback;
        fallback = this.templates.get(CANNON_V1_ROT0);
        if (fallback != null) return fallback;
        return this.templates.isEmpty() ? null : this.templates.values().iterator().next();
    }
}
