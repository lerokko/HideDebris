package xyz.skyfalls.hidedebris.utils;

public record Vec2i(int x, int y) {
    public int z() {
        return this.y;
    }

    public static Vec2i fromStrings(String x, String y) {
        return new Vec2i(Integer.parseInt(x), Integer.parseInt(y));
    }
}
