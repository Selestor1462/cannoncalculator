package dev.selestor1462.cannonencoder;

import dev.selestor1462.cannonencoder.calc.ftl.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FtlEncodingTest {

    @Test
    public void testCase1NormalFtl() {
        // early=300, late=200, upaccel=13, lr=False, dir=3, angle=2, cannonMax=6688
        FtlEncodingData enc = FtlEncodingData.compute(
            300, 200, 13, false, 3, 2, new Vec3d(1000, 255, 2000), 6688
        );

        String expected = 
            "purple:      [101]\n" +
            "blue:        [10]\n" +
            "cyan:        [0100]\n" +
            "light_blue:  [000]\n" +
            "lime:        [111001]\n" +
            "yellow:      [1100]\n" +
            "orange:      [101011]\n" +
            "red:         [000]\n" +
            "pink:        [1100]\n" +
            "magenta:     [00]\n" +
            "purple:      [01]\n\n" +
            "place a block at: 1000 255 2000";

        assertEquals(expected, enc.formatWebsiteOutput());

        boolean[] bits = enc.getAllBits();
        assertEquals(39, bits.length);
        // Angle [10] (purple2=2) -> bit 0 is true, bit 1 is false
        assertTrue(bits[0]);
        assertFalse(bits[1]);
        // Magenta [00] -> bits 2, 3 false
        assertFalse(bits[2]);
        assertFalse(bits[3]);
        // Pink [0011] (pink=3) -> bits 4=F, 5=F, 6=T, 7=T
        assertFalse(bits[4]);
        assertFalse(bits[5]);
        assertTrue(bits[6]);
        assertTrue(bits[7]);
        // Red [000] -> bits 8, 9, 10 = F
        assertFalse(bits[8]);
        assertFalse(bits[9]);
        assertFalse(bits[10]);
        // Yellow (dir=3, lr=false -> "1100") -> bit 17=T, bit 18=T, bit 19=F, bit 20=F
        assertTrue(bits[17]);
        assertTrue(bits[18]);
        assertFalse(bits[19]);
        assertFalse(bits[20]);
    }

    @Test
    public void testCase2Variable1() {
        // early=100, late=3500, upaccel=25, lr=True, dir=5, angle=1, cannonMax=6688
        FtlEncodingData enc = FtlEncodingData.compute(
            100, 3500, 25, true, 5, 1, new Vec3d(500, 255, 600), 6688
        );

        boolean[] bits = enc.getAllBits();
        // Magenta [10] -> magenta=1 -> first char '1' at bit 3 (Z=-46), second char '0' at bit 2 (Z=-47)
        assertFalse(bits[2]);
        assertTrue(bits[3]);
        // Yellow: dir=5, lr=true -> yellow=13 -> binary is "1101", rev is "1011" -> bit 17=T, bit 18=F, bit 19=T, bit 20=T
        assertTrue(bits[17]);
        assertFalse(bits[18]);
        assertTrue(bits[19]);
        assertTrue(bits[20]);
    }

    @Test
    public void testRedEncoding001() {
        // Test user's specific case: calculator output is 001 (e.g. red=1 -> +418 TNT)
        // Red weights are [4, 2, 1]
        FtlEncodingData enc = FtlEncodingData.compute(
            418, 0, 0, false, 0, 0, null, 6688
        );
        assertEquals("001", FtlEncodingData.binary(enc.red(), new int[]{4, 2, 1}));

        boolean[] bits = enc.getAllBits();
        // In the world, bit 8 (+1672) is F, bit 9 (+836) is F, bit 10 (+418) is T
        assertFalse(bits[8]);
        assertFalse(bits[9]);
        assertTrue(bits[10]);
    }

    @Test
    public void testDirection4LongRange() {
        // Destination 20000, 20000: direction=4 (SSE), lr=true -> yellow=12 (8+4)
        FtlEncodingData enc = FtlEncodingData.compute(
            200, 205, 14, true, 4, 0, new Vec3d(20017, 255, 20017), 6688
        );
        // Website format: yellow is [0011] (rev("1100"))
        assertEquals("0011", FtlEncodingData.rev(FtlEncodingData.binary(enc.yellow(), new int[]{8, 4, 2, 1})));

        boolean[] bits = enc.getAllBits();
        // Physical cannon: matches website string "0011" bit-for-bit (bit 17..20: F, F, T, T)
        assertFalse(bits[17]);
        assertFalse(bits[18]);
        assertTrue(bits[19]);
        assertTrue(bits[20]);
    }

    @Test
    public void testYellow0001() {
        // LongRange=true, direction=0 -> yellow=8 -> binary is 1000, reversed is 0001
        FtlEncodingData enc = FtlEncodingData.compute(
            100, 100, 0, true, 0, 0, null, 6688
        );
        assertEquals("0001", FtlEncodingData.rev(FtlEncodingData.binary(enc.yellow(), new int[]{8, 4, 2, 1})));

        boolean[] bits = enc.getAllBits();
        // Matches user's exact requirement: bit 17=F, 18=F, 19=F, 20=T
        assertFalse(bits[17]);
        assertFalse(bits[18]);
        assertFalse(bits[19]);
        assertTrue(bits[20]);
    }

    @Test
    public void testHalfCannonRemovesThirdBigBits() {
        // Half cannon: 3344 max TNT -> early max 1672, late max 1672
        FtlEncodingData enc = FtlEncodingData.compute(
            1672, 1672, 0, false, 0, 0, null, 3344
        );
        boolean[] bits = enc.getAllBits();
        // Bit 8 (Red 4 - third red bit) must NOT be set
        assertFalse(bits[8]);
        // Bit 9 (Red 2) and Bit 10 (Red 1) can be set
        assertTrue(bits[9]);
        assertTrue(bits[10]);
        // Bit 27 (Light Blue 4 - third light blue bit) must NOT be set
        assertFalse(bits[27]);
        assertTrue(bits[28]);
        assertTrue(bits[29]);
    }

    @Test
    public void testQuarterCannonRemovesSecondAndThirdBigBits() {
        // 1/4 cannon: 1672 max TNT -> early max 836, late max 836
        FtlEncodingData enc = FtlEncodingData.compute(
            836, 836, 0, false, 0, 0, null, 1672
        );
        boolean[] bits = enc.getAllBits();
        // Bits 8 and 9 (Red 4, Red 2) must NOT be set
        assertFalse(bits[8]);
        assertFalse(bits[9]);
        assertTrue(bits[10]);
        // Bits 27 and 28 (Light Blue 4, Light Blue 2) must NOT be set
        assertFalse(bits[27]);
        assertFalse(bits[28]);
        assertTrue(bits[29]);
    }

    @Test
    public void testUserUploadedSolution1Screenshot() {
        // From user screenshot 1:
        // 672 TNT (373 Early, 299 Late), Upaccel: 0, LongRange: false, Direction: SSE (4) or ESE (6)?
        // Screenshot shows:
        // pur: 000 (upaccel small)
        // blu: 00 (upaccel big)
        // cya: 0100 (late small)
        // ltb: 000 (late big)
        // lim: 101011 (late medium)
        // yel: 0010 (direction: rev("0100") -> yellow=4)
        // ora: 110111 (early medium)
        // red: 000 (early big)
        // pnk: 0111 (early small)
        // mag: 00 (normal FTL, early and late <= half)
        // pu2: 00 (angle 0)
        FtlEncodingData enc = FtlEncodingData.compute(
            373, 299, 0, false, 4, 0, new Vec3d(1230, 255, 1287), 6688
        );

        assertEquals("000", FtlEncodingData.rev(FtlEncodingData.binary(enc.purple(), new int[]{4, 2, 1})));
        assertEquals("00", FtlEncodingData.rev(FtlEncodingData.binary(enc.blue(), new int[]{2, 1})));
        assertEquals("0100", FtlEncodingData.rev(FtlEncodingData.binary(enc.cyan(), new int[]{4, 4, 2, 1})));
        assertEquals("000", FtlEncodingData.rev(FtlEncodingData.binary(enc.lightBlue(), new int[]{4, 2, 1})));
        assertEquals("101011", FtlEncodingData.rev(FtlEncodingData.binary(enc.lime(), new int[]{11, 11, 8, 4, 2, 1})));
        assertEquals("0010", FtlEncodingData.rev(FtlEncodingData.binary(enc.yellow(), new int[]{8, 4, 2, 1})));
        assertEquals("110111", FtlEncodingData.rev(FtlEncodingData.binary(enc.orange(), new int[]{11, 11, 8, 4, 2, 1})));
        assertEquals("000", FtlEncodingData.rev(FtlEncodingData.binary(enc.red(), new int[]{4, 2, 1})));
        assertEquals("0111", FtlEncodingData.rev(FtlEncodingData.binary(enc.pink(), new int[]{4, 4, 2, 1})));
        assertEquals("00", FtlEncodingData.rev(FtlEncodingData.binary(enc.magenta(), new int[]{2, 1})));
        assertEquals("00", FtlEncodingData.rev(FtlEncodingData.binary(enc.purple2(), new int[]{2, 1})));

        // Verify the entire formatted string exactly matches the GUI / Web output
        String expectedWebsite =
            "purple:      [000]\n" +
            "blue:        [00]\n" +
            "cyan:        [0100]\n" +
            "light_blue:  [000]\n" +
            "lime:        [101011]\n" +
            "yellow:      [0010]\n" +
            "orange:      [110111]\n" +
            "red:         [000]\n" +
            "pink:        [0111]\n" +
            "magenta:     [00]\n" +
            "purple:      [00]\n\n" +
            "place a block at: 1230 255 1287";
        assertEquals(expectedWebsite, enc.formatWebsiteOutput());
    }

    @Test
    public void testVariableEarlyVsVariableLateMagentaEncoding() {
        // Normal FTL (both <= half): magenta = 0 -> "00"
        FtlEncodingData normal = FtlEncodingData.compute(1000, 1000, 0, false, 0, 0, null, 6688);
        assertEquals(0, normal.magenta());
        assertEquals("00", FtlEncodingData.rev(FtlEncodingData.binary(normal.magenta(), new int[]{2, 1})));

        // Variable Late (early < late): magenta = 1 -> binary(1, [2, 1]) is "01" -> rev is "10"
        FtlEncodingData varLate = FtlEncodingData.compute(500, 4000, 0, false, 0, 0, null, 6688);
        assertEquals(1, varLate.magenta());
        assertEquals("10", FtlEncodingData.rev(FtlEncodingData.binary(varLate.magenta(), new int[]{2, 1})));

        // Variable Early (late < early): magenta = 2 -> binary(2, [2, 1]) is "10" -> rev is "01"
        FtlEncodingData varEarly = FtlEncodingData.compute(4000, 500, 0, false, 0, 0, null, 6688);
        assertEquals(2, varEarly.magenta());
        assertEquals("01", FtlEncodingData.rev(FtlEncodingData.binary(varEarly.magenta(), new int[]{2, 1})));
    }

    @Test
    public void testAllEightDirectionsWithAndWithoutLongRange() {
        // Yellow bits format: rev(binary(yellow, [8, 4, 2, 1]))
        // For dir in 0..7 without Long Range (yellow = dir):
        // dir=0 (0000 -> 0000)
        // dir=1 (0001 -> 1000)
        // dir=2 (0010 -> 0100)
        // dir=3 (0011 -> 1100)
        // dir=4 (0100 -> 0010)
        // dir=5 (0101 -> 1010)
        // dir=6 (0110 -> 0110)
        // dir=7 (0111 -> 1110)
        String[] expectedShort = {"0000", "1000", "0100", "1100", "0010", "1010", "0110", "1110"};
        // For dir in 0..7 WITH Long Range (yellow = dir + 8):
        // dir=0 (1000 -> 0001)
        // dir=1 (1001 -> 1001)
        // dir=2 (1010 -> 0101)
        // dir=3 (1011 -> 1101)
        // dir=4 (1100 -> 0011)
        // dir=5 (1101 -> 1011)
        // dir=6 (1110 -> 0111)
        // dir=7 (1111 -> 1111)
        String[] expectedLong = {"0001", "1001", "0101", "1101", "0011", "1011", "0111", "1111"};

        for (int dir = 0; dir < 8; dir++) {
            FtlEncodingData encShort = FtlEncodingData.compute(100, 100, 0, false, dir, 0, null, 6688);
            assertEquals(expectedShort[dir], FtlEncodingData.rev(FtlEncodingData.binary(encShort.yellow(), new int[]{8, 4, 2, 1})));

            FtlEncodingData encLong = FtlEncodingData.compute(100, 100, 0, true, dir, 0, null, 6688);
            assertEquals(expectedLong[dir], FtlEncodingData.rev(FtlEncodingData.binary(encLong.yellow(), new int[]{8, 4, 2, 1})));
        }
    }

    @Test
    public void testUserDest25Minus200k() {
        FtlCalculator calc = new FtlCalculator();
        Vec3d pearl = new Vec3d(200.51, FtlConstants.PEARL_Y, 8.51);
        Vec3d dest = new Vec3d(25.0, 256.0, -200000.0);
        java.util.List<FtlCalculationResult> results = calc.calculate(pearl, dest, 6688, 500, 50.0, 3344);
        assertFalse(results.isEmpty());
        FtlCalculationResult r0 = results.get(0);
        assertEquals(14, r0.earlyTnt());
        assertEquals(2240, r0.lateTnt());
        assertEquals(2254, r0.totalTnt());
        assertEquals(1, r0.encodingData().magenta());
        assertEquals("10", FtlEncodingData.rev(FtlEncodingData.binary(r0.encodingData().magenta(), new int[]{2, 1})));

        // In the physical cannon:
        // Website "10": first char '1' at bit 3 (Z=-46), second char '0' at bit 2 (Z=-47)
        boolean[] bits = r0.encodingData().getAllBits();
        assertFalse(bits[2]);
        assertTrue(bits[3]);
    }

    @Test
    public void testUserDestMinus200k() {
        FtlCalculator calc = new FtlCalculator();
        Vec3d pearl = new Vec3d(8.51, FtlConstants.PEARL_Y, 8.51);
        Vec3d dest = new Vec3d(-200000.0, 256.0, 200000.0);
        java.util.List<FtlCalculationResult> results = calc.calculate(pearl, dest, 6688, 500, 50.0, 3344);
        assertFalse(results.isEmpty());
        assertEquals(55, results.size());
        FtlCalculationResult r0 = results.get(0);
        assertEquals(3313, r0.earlyTnt());
        assertEquals(1, r0.lateTnt());
        assertEquals(3314, r0.totalTnt());
        assertEquals(218, r0.ticks());
        assertEquals(11.045, Math.round(r0.distance() * 1000.0) / 1000.0);
        assertEquals(2, r0.encodingData().magenta());
        assertEquals("01", FtlEncodingData.rev(FtlEncodingData.binary(r0.encodingData().magenta(), new int[]{2, 1})));
    }

    @Test
    public void testUserDestWebScreenshot2() {
        FtlCalculator calc = new FtlCalculator();
        Vec3d pearl = new Vec3d(200.51, FtlConstants.PEARL_Y, 8.51);
        Vec3d dest = new Vec3d(25.0, 256.0, -200000.0);
        java.util.List<FtlCalculationResult> results = calc.calculate(pearl, dest, 6688, 500, 50.0, 3344);
        assertFalse(results.isEmpty());
        assertEquals(135, results.size());
        FtlCalculationResult r0 = results.get(0);
        assertEquals(14, r0.earlyTnt());
        assertEquals(2240, r0.lateTnt());
        assertEquals(2254, r0.totalTnt());
        assertEquals(259, r0.ticks());
        assertEquals(3.586, Math.round(r0.distance() * 1000.0) / 1000.0);
    }

    @Test
    public void testUserDest500kAnd1() {
        FtlCalculator calc = new FtlCalculator();
        Vec3d pearl = new Vec3d(0.51, FtlConstants.PEARL_Y, 0.51);
        Vec3d dest = new Vec3d(500000.0, 256.0, 1.0);
        java.util.List<FtlCalculationResult> res6688 = calc.calculate(pearl, dest, 6688, 500, 50.0, 6688);
        assertFalse(res6688.isEmpty());
        assertEquals(72, res6688.size());
    }

    @Test
    public void testFtlSortingOptions() {
        FtlCalculator calc = new FtlCalculator();
        Vec3d pearl = new Vec3d(200.51, FtlConstants.PEARL_Y, 8.51);
        Vec3d dest = new Vec3d(25.0, 256.0, -200000.0);
        java.util.List<FtlCalculationResult> list = new java.util.ArrayList<>(calc.calculate(pearl, dest, 6688, 500, 50.0, 3344));
        assertFalse(list.isEmpty());

        // Sort by TNT ascending
        list.sort(java.util.Comparator.comparingInt(FtlCalculationResult::totalTnt));
        for (int i = 0; i < list.size() - 1; i++) {
            assertTrue(list.get(i).totalTnt() <= list.get(i + 1).totalTnt());
        }

        // Sort by Ticks ascending
        list.sort(java.util.Comparator.comparingInt(FtlCalculationResult::ticks));
        for (int i = 0; i < list.size() - 1; i++) {
            assertTrue(list.get(i).ticks() <= list.get(i + 1).ticks());
        }

        // Sort by Destination (distance) ascending
        list.sort(java.util.Comparator.comparingDouble(FtlCalculationResult::distance));
        for (int i = 0; i < list.size() - 1; i++) {
            assertTrue(list.get(i).distance() <= list.get(i + 1).distance());
        }
    }
}
