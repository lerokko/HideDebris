package xyz.skyfalls.hidedebris.utils;

import java.util.stream.Stream;

public record Vec3i(int x, int y, int z) {
    public Vec3i up() {
        return new Vec3i(x, y + 1, z);
    }

    public Vec3i down() {
        return new Vec3i(x, y - 1, z);
    }

    public Stream<Vec3i> around() {
        return Stream.of(
                // prioritizing up and down for better camo
                new Vec3i(x, y + 1, z),
                new Vec3i(x, y - 1, z),
                new Vec3i(x - 1, y, z),
                new Vec3i(x, y, z - 1),
                new Vec3i(x + 1, y, z),
                new Vec3i(x, y, z + 1)
        );
    }
}
