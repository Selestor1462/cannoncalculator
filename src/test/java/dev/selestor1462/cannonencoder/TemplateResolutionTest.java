package dev.selestor1462.cannonencoder;

import dev.selestor1462.cannonencoder.calc.CalculationResult;
import dev.selestor1462.cannonencoder.template.HighlightPlan;
import dev.selestor1462.cannonencoder.template.TemplateRegistry;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TemplateResolutionTest {

    @Test
    public void testUserPigCannonResolution() {
        TemplateRegistry registry = TemplateRegistry.createDefault();
        BlockPos anchor = new BlockPos(2, 143, -11);
        CalculationResult calc = new CalculationResult(
            2, -11, 52592, -5192, 52590, -5180, 52592, -5191,
            4733.1, -466.2, 189, 32, 20, 36, 5
        );

        Optional<HighlightPlan> planOpt = registry.resolve("cannon_v1_rot0", anchor, calc, 2, 1);
        assertTrue(planOpt.isPresent(), "cannon_v1_rot0 should resolve successfully");
        HighlightPlan plan = planOpt.get();
        assertFalse(plan.blocks().isEmpty(), "Blocks should not be empty");
    }
}
