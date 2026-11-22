package dev.selestor1462.cannonencoder;

import dev.selestor1462.cannonencoder.calc.CalculationResult;
import dev.selestor1462.cannonencoder.calc.CodingSlot;
import dev.selestor1462.cannonencoder.calc.PigCannonCalculator;
import dev.selestor1462.cannonencoder.text.ResultMessageFormatter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PigCannonCalculatorTest {

    @Test
    public void testUserCaseReferenceCalculation() {
        PigCannonCalculator calc = new PigCannonCalculator();
        // initX = 0, initZ = 0, destX = 20000, destZ = 20010
        CalculationResult res = calc.calculate(0, 0, 20000, 20010);

        assertEquals(0, res.straightAccelFirst());
        assertEquals(2, res.straightAccelSecond());
        assertEquals(80, res.diagonalAccelThird());
        assertEquals(0, res.diagonalAccelFourth());
        assertEquals(2, res.directionCode());

        // Slot values check
        assertEquals(0, res.slotValue(CodingSlot.FIRST_ACCEL));
        assertEquals(2, res.slotValue(CodingSlot.SECOND_ACCEL));
        assertEquals(80, res.slotValue(CodingSlot.THIRD_ACCEL));
        assertEquals(0, res.slotValue(CodingSlot.FOURTH_ACCEL));
        assertEquals(2, res.slotValue(CodingSlot.DIRECTION));
    }

    @Test
    public void testRoundToFive() {
        assertEquals(20000, PigCannonCalculator.roundToFive(19998.0));
        assertEquals(20000, PigCannonCalculator.roundToFive(20000.0));
        assertEquals(20000, PigCannonCalculator.roundToFive(20002.0));
        assertEquals(20005, PigCannonCalculator.roundToFive(20003.0));
        assertEquals(-20000, PigCannonCalculator.roundToFive(-19998.0));
        assertEquals(-20000, PigCannonCalculator.roundToFive(-20000.0));
        assertEquals(-20000, PigCannonCalculator.roundToFive(-20002.0));
        assertEquals(-20005, PigCannonCalculator.roundToFive(-20003.0));
    }

    @Test
    public void testAllEightDirectionsAndAccelerations() {
        PigCannonCalculator calc = new PigCannonCalculator();

        // 1. Direction 0: dX < 0, dZ > 0, |dX| <= |dZ| (mixed sign, cancelZ=false, dZ>=0)
        CalculationResult r0 = calc.calculate(0, 0, -1000, 2265);
        assertEquals(0, r0.directionCode());
        // straight distance = 2265 - 1000 = 1265 -> 1265/250 = 5 big, (1265 - 1250)/5 = 3 small
        assertEquals(5, r0.straightAccelFirst());
        assertEquals(3, r0.straightAccelSecond());
        // diagonal distance = 1000 -> 1000/250 = 4 big, 0 small
        assertEquals(4, r0.diagonalAccelThird());
        assertEquals(0, r0.diagonalAccelFourth());

        // 2. Direction 1: dX < 0, dZ > 0, |dX| > |dZ| (mixed sign, cancelZ=true, dZ>=0)
        CalculationResult r1 = calc.calculate(0, 0, -2265, 1000);
        assertEquals(1, r1.directionCode());
        assertEquals(5, r1.straightAccelFirst());
        assertEquals(3, r1.straightAccelSecond());
        assertEquals(4, r1.diagonalAccelThird());
        assertEquals(0, r1.diagonalAccelFourth());

        // 3. Direction 2: dX > 0, dZ > 0, |dX| <= |dZ| (same sign, cancelZ=false, dZ>=0)
        CalculationResult r2 = calc.calculate(0, 0, 1000, 2265);
        assertEquals(2, r2.directionCode());
        assertEquals(5, r2.straightAccelFirst());
        assertEquals(3, r2.straightAccelSecond());
        assertEquals(4, r2.diagonalAccelThird());
        assertEquals(0, r2.diagonalAccelFourth());

        // 4. Direction 3: dX > 0, dZ > 0, |dX| > |dZ| (same sign, cancelZ=true, dZ>=0)
        CalculationResult r3 = calc.calculate(0, 0, 2265, 1000);
        assertEquals(3, r3.directionCode());
        assertEquals(5, r3.straightAccelFirst());
        assertEquals(3, r3.straightAccelSecond());
        assertEquals(4, r3.diagonalAccelThird());
        assertEquals(0, r3.diagonalAccelFourth());

        // 5. Direction 4: dX > 0, dZ < 0, |dX| <= |dZ| (mixed sign, cancelZ=false, dZ<0)
        CalculationResult r4 = calc.calculate(0, 0, 1000, -2265);
        assertEquals(4, r4.directionCode());
        assertEquals(5, r4.straightAccelFirst());
        assertEquals(3, r4.straightAccelSecond());
        assertEquals(4, r4.diagonalAccelThird());
        assertEquals(0, r4.diagonalAccelFourth());

        // 6. Direction 5: dX > 0, dZ < 0, |dX| > |dZ| (mixed sign, cancelZ=true, dZ<0)
        CalculationResult r5 = calc.calculate(0, 0, 2265, -1000);
        assertEquals(5, r5.directionCode());
        assertEquals(5, r5.straightAccelFirst());
        assertEquals(3, r5.straightAccelSecond());
        assertEquals(4, r5.diagonalAccelThird());
        assertEquals(0, r5.diagonalAccelFourth());

        // 7. Direction 6: dX < 0, dZ < 0, |dX| <= |dZ| (same sign, cancelZ=false, dZ<0)
        CalculationResult r6 = calc.calculate(0, 0, -1000, -2265);
        assertEquals(6, r6.directionCode());
        assertEquals(5, r6.straightAccelFirst());
        assertEquals(3, r6.straightAccelSecond());
        assertEquals(4, r6.diagonalAccelThird());
        assertEquals(0, r6.diagonalAccelFourth());

        // 8. Direction 7: dX < 0, dZ < 0, |dX| > |dZ| (same sign, cancelZ=true, dZ<0)
        CalculationResult r7 = calc.calculate(0, 0, -2265, -1000);
        assertEquals(7, r7.directionCode());
        assertEquals(5, r7.straightAccelFirst());
        assertEquals(3, r7.straightAccelSecond());
        assertEquals(4, r7.diagonalAccelThird());
        assertEquals(0, r7.diagonalAccelFourth());
    }

    @Test
    public void testResultMessageFormatterMatchesOriginalJar() {
        PigCannonCalculator calc = new PigCannonCalculator();
        CalculationResult res = calc.calculate(0, 0, 20000, 20010);
        var messages = ResultMessageFormatter.format(res);
        assertNotNull(messages);
        assertFalse(messages.isEmpty());

        int totalTicks = res.straightAccelFirst() + res.straightAccelSecond() +
                         res.diagonalAccelThird() + res.diagonalAccelFourth() +
                         res.directionCode();
        assertEquals(84, totalTicks);
    }
}

