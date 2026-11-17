package dev.jdguo.shop.outlet;

/** Clockwise quarter turns from the legacy north-facing model. */
public final class CounterRotation {
    private CounterRotation() { }
    public static int normalize(int turns) { return Math.floorMod(turns, 4); }
    public static String name(int turns) { return new String[]{"north", "east", "south", "west"}[normalize(turns)]; }
    public static double x(int turns, double x, double z) { return switch(normalize(turns)) { case 1 -> -z; case 2 -> -x; case 3 -> z; default -> x; }; }
    public static double z(int turns, double x, double z) { return switch(normalize(turns)) { case 1 -> x; case 2 -> -z; case 3 -> -x; default -> z; }; }
}
