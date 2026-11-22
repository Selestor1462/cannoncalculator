package dev.selestor1462.cannonencoder.calc.ftl;

public record FtlEncodingData(
    int purple,     // Upaccel small (4, 2, 1)
    int blue,       // Upaccel big (2, 1)
    int cyan,       // Late small (4, 4, 2, 1)
    int lightBlue,  // Late big (4, 2, 1)
    int lime,       // Late medium (11, 11, 8, 4, 2, 1)
    int yellow,     // Direction (1, 2, 4) + LongRange (8)
    int orange,     // Early medium (11, 11, 8, 4, 2, 1)
    int red,        // Early big (4, 2, 1)
    int pink,       // Early small (4, 4, 2, 1)
    int magenta,    // Variable (1 = Early, 2 = Late)
    int purple2,    // Angle (2, 1)
    Vec3d blocker,
    int cannonMaxTnt
) {
    public static final int TOTAL_BITS = 39;

    public FtlEncodingData(
        int purple,
        int blue,
        int cyan,
        int lightBlue,
        int lime,
        int yellow,
        int orange,
        int red,
        int pink,
        int magenta,
        int purple2,
        Vec3d blocker
    ) {
        this(purple, blue, cyan, lightBlue, lime, yellow, orange, red, pink, magenta, purple2, blocker, 6688);
    }

    public static FtlEncodingData compute(
        int earlyTnt,
        int lateTnt,
        int upaccelTnt,
        boolean longRange,
        int direction,
        int angle,
        Vec3d blocker,
        int cannonMaxTnt
    ) {
        int blue = upaccelTnt / 8;
        int purple = upaccelTnt % 8;
        int yellow = direction + (longRange ? 8 : 0);
        int purple2 = angle;

        int half = cannonMaxTnt / 2;
        int maxVariableTnt = cannonMaxTnt - 22;

        if (earlyTnt <= half && lateTnt <= half) {
            int[] redBits = splitTnt(earlyTnt, cannonMaxTnt);
            int[] blueBits = splitTnt(lateTnt, cannonMaxTnt);
            return new FtlEncodingData(
                purple, blue, blueBits[2], blueBits[0], blueBits[1],
                yellow, redBits[1], redBits[0], redBits[2],
                0, purple2, blocker, cannonMaxTnt
            );
        }

        if (earlyTnt < lateTnt) {
            int magenta = 1;
            int redTntInUse = (earlyTnt / 11) * 11;
            int[] redBits = splitTnt(earlyTnt, cannonMaxTnt);
            int[] blueBits = splitTnt(lateTnt - (maxVariableTnt / 2 - redTntInUse), cannonMaxTnt);
            return new FtlEncodingData(
                purple, blue, blueBits[2], blueBits[0], blueBits[1],
                yellow, redBits[1], redBits[0], redBits[2],
                magenta, purple2, blocker, cannonMaxTnt
            );
        }

        if (lateTnt < earlyTnt) {
            int magenta = 2;
            int redTntSingle = earlyTnt % 11;
            int blueTntSingle = lateTnt % 11;
            int earlyAdj = earlyTnt - redTntSingle;
            int lateAdj = lateTnt - blueTntSingle;
            int redTnt = maxVariableTnt / 2 - lateAdj;
            int blueTnt = earlyAdj - redTnt;
            int[] redBits = splitTnt(redTnt + redTntSingle, cannonMaxTnt);
            int[] blueBits = splitTnt(blueTnt + blueTntSingle, cannonMaxTnt);
            return new FtlEncodingData(
                purple, blue, blueBits[2], blueBits[0], blueBits[1],
                yellow, redBits[1], redBits[0], redBits[2],
                magenta, purple2, blocker, cannonMaxTnt
            );
        }

        return new FtlEncodingData(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, blocker, cannonMaxTnt);
    }

    private static int[] splitTnt(int tnt, int cannonMaxTnt) {
        int maxBig = 7;
        if (cannonMaxTnt <= 1672) {
            maxBig = 1;
        } else if (cannonMaxTnt <= 3344) {
            maxBig = 3;
        }
        int big = Math.min(maxBig, tnt / 418);
        int rem = tnt - big * 418;
        int medium = rem / 11;
        int small = rem % 11;
        return new int[]{big, medium, small};
    }

    public static String binary(int n, int[] values) {
        StringBuilder sb = new StringBuilder();
        int val = n;
        for (int v : values) {
            if (val >= v) {
                val -= v;
                sb.append('1');
            } else {
                sb.append('0');
            }
        }
        return sb.toString();
    }

    public static String rev(String s) {
        return new StringBuilder(s).reverse().toString();
    }

    public String formatEncodingOutput() {
        String blockStr = (this.blocker != null)
            ? String.format("%d %d %d", (int) Math.floor(this.blocker.x()), (int) Math.floor(this.blocker.y()), (int) Math.floor(this.blocker.z()))
            : "none";

        return String.format(
            "purple:      [%s]\n" +
            "blue:        [%s]\n" +
            "cyan:        [%s]\n" +
            "light_blue:  [%s]\n" +
            "lime:        [%s]\n" +
            "yellow:      [%s]\n" +
            "orange:      [%s]\n" +
            "red:         [%s]\n" +
            "pink:        [%s]\n" +
            "magenta:     [%s]\n" +
            "purple:      [%s]\n\n" +
            "place a block at: %s",
            rev(binary(this.purple,     new int[]{4, 2, 1})),
            rev(binary(this.blue,       new int[]{2, 1})),
            rev(binary(this.cyan,       new int[]{4, 4, 2, 1})),
            rev(binary(this.lightBlue,  new int[]{4, 2, 1})),
            rev(binary(this.lime,       new int[]{11, 11, 8, 4, 2, 1})),
            rev(binary(this.yellow,     new int[]{8, 4, 2, 1})),
            rev(binary(this.orange,     new int[]{11, 11, 8, 4, 2, 1})),
            rev(binary(this.red,        new int[]{4, 2, 1})),
            rev(binary(this.pink,       new int[]{4, 4, 2, 1})),
            rev(binary(this.magenta,    new int[]{2, 1})),
            rev(binary(this.purple2,    new int[]{2, 1})),
            blockStr
        );
    }

    public String formatWebsiteOutput() {
        return formatEncodingOutput();
    }

    public boolean[] getAllBits() {
        boolean[] bits = new boolean[TOTAL_BITS];

        String sAngle = binary(this.purple2, new int[]{2, 1});
        bits[0] = sAngle.charAt(0) == '1';
        bits[1] = sAngle.charAt(1) == '1';

        String sMagenta = rev(binary(this.magenta, new int[]{2, 1}));
        // Physical cannon bit mapping along Z axis:
        // Position 1 (closer to controls at Z=-46) is bit 3 (first char of website string, e.g. '1' in "10")
        // Position 2 (Z=-47) is bit 2 (second char of website string, e.g. '0' in "10")
        bits[2] = (sMagenta.charAt(1) == '1');
        bits[3] = (sMagenta.charAt(0) == '1');

        String sPink = binary(this.pink, new int[]{4, 4, 2, 1});
        for (int i = 0; i < 4; i++) bits[4 + i] = (sPink.charAt(i) == '1');

        String sRed = binary(this.red, new int[]{4, 2, 1});
        for (int i = 0; i < 3; i++) bits[8 + i] = (sRed.charAt(i) == '1');

        String sOrange = binary(this.orange, new int[]{11, 11, 8, 4, 2, 1});
        for (int i = 0; i < 6; i++) bits[11 + i] = (sOrange.charAt(i) == '1');

        // Physical cannon direction bits at Z = -34..-31:
        // Matches website output rev(binary(this.yellow, [8, 4, 2, 1])) bit-for-bit
        String sYellow = rev(binary(this.yellow, new int[]{8, 4, 2, 1}));
        for (int i = 0; i < 4; i++) bits[17 + i] = (sYellow.charAt(i) == '1');

        String sLime = binary(this.lime, new int[]{11, 11, 8, 4, 2, 1});
        for (int i = 0; i < 6; i++) bits[21 + i] = (sLime.charAt(i) == '1');

        String sLightBlue = binary(this.lightBlue, new int[]{4, 2, 1});
        for (int i = 0; i < 3; i++) bits[27 + i] = (sLightBlue.charAt(i) == '1');

        String sCyan = binary(this.cyan, new int[]{4, 4, 2, 1});
        for (int i = 0; i < 4; i++) bits[30 + i] = (sCyan.charAt(i) == '1');

        String sBlue = binary(this.blue, new int[]{2, 1});
        for (int i = 0; i < 2; i++) bits[34 + i] = (sBlue.charAt(i) == '1');

        String sPurple = binary(this.purple, new int[]{4, 2, 1});
        for (int i = 0; i < 3; i++) bits[36 + i] = (sPurple.charAt(i) == '1');

        return bits;
    }

    public static boolean isTargetBit(int bitIndex) {
        // bit 3 is Variable 2, bit 22 is Lime second 11
        return bitIndex == 3 || bitIndex == 22;
    }

    public boolean isBitSet(int bitIndex) {
        boolean[] bits = this.getAllBits();
        if (bitIndex >= 0 && bitIndex < bits.length) {
            return bits[bitIndex];
        }
        return false;
    }

    public int getBitColor(int bitIndex) {
        if (bitIndex >= 0 && bitIndex <= 1) return 0xFFAA00AA;   // Purple (Angle)
        if (bitIndex >= 2 && bitIndex <= 3) return 0xFFFF55FF;   // Magenta (Variable)
        if (bitIndex >= 4 && bitIndex <= 7) return 0xFFFF69B4;   // Pink (Early small)
        if (bitIndex >= 8 && bitIndex <= 10) return 0xFFFF2020;  // Red (Early big)
        if (bitIndex >= 11 && bitIndex <= 16) return 0xFFFF8C00; // Orange (Early medium)
        if (bitIndex >= 17 && bitIndex <= 19) return 0xFFFFD700; // Yellow (Direction)
        if (bitIndex == 20) return 0xFF555555;                   // Gray/Black (Long Range)
        if (bitIndex >= 21 && bitIndex <= 26) return 0xFF44FF44; // Lime (Late medium)
        if (bitIndex >= 27 && bitIndex <= 29) return 0xFF29B6F6; // Light Blue (Late big)
        if (bitIndex >= 30 && bitIndex <= 33) return 0xFF00CED1; // Cyan (Late small)
        if (bitIndex >= 34 && bitIndex <= 35) return 0xFF1E90FF; // Blue (Upaccel big)
        if (bitIndex >= 36 && bitIndex <= 38) return 0xFF8A2BE2; // Purple (Upaccel small)
        return 0xFFFFFFFF;
    }
}
