package xyz.skyfalls.hidedebris.utils;

import org.bukkit.World;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class RegionUtils {
    private final static Pattern MCA_PATTERN = Pattern.compile("^r\\.(-?\\d+)\\.(-?\\d+).mca");

    public static Stream<Vec2i> getChunksInRegion(Vec2i region) {
        return IntStream.range(0, 32 * 32)
                .mapToObj(i -> new Vec2i(region.x() * 32 + i / 32, region.y() * 32 + i % 32));
    }

    public static List<Vec2i> getRegions(World world) throws IOException {
        var folder = world.getWorldFolder().toPath().resolve("DIM-1/region");
        try (var list = Files.list(folder)) {
            return list.map(e -> {
                var matcher = MCA_PATTERN.matcher(e.getFileName().toString());
                if (matcher.find()) {
                    return Vec2i.fromStrings(matcher.group(1), matcher.group(2));
                } else {
                    throw new IllegalArgumentException("Unexpected file in region folder " + e);
                }
            }).toList();
        }
    }
}
