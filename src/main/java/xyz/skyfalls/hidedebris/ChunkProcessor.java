package xyz.skyfalls.hidedebris;

import org.bukkit.Material;
import xyz.skyfalls.hidedebris.utils.Vec3i;

import java.util.*;
import java.util.stream.Collectors;

public class ChunkProcessor {
    private final static Set<Material> TYPES_SURROUND = new HashSet<>(Set.of(
            Material.NETHERRACK, Material.SOUL_SAND, Material.MAGMA_BLOCK,
            Material.GRAVEL, Material.BLACKSTONE, Material.BASALT,
            Material.NETHER_QUARTZ_ORE, Material.NETHER_GOLD_ORE));
    private final static Set<Material> TYPES_EXPOSED = new HashSet<>(Set.of(Material.LAVA));

    public static HashMap<Vec3i, Boolean> scanDebris(IRegionAccess.Inbounds chunk, int minY, int maxY) {
        return scanDebris(chunk, minY, maxY, 0);
    }

    // returns all ancient debris, and a pushable flag
    // extraBlocks is to cover newly generated ore decoration which can go across chunk borders,
    // requires outbounds access if extraBlocks > 0
    public static HashMap<Vec3i, Boolean> scanDebris(IRegionAccess chunk, int minY, int maxY, int extraBlocks) {
        HashMap<Vec3i, Boolean> debris = new HashMap<>();
        // scan top to bottom
        for (int y = maxY; y >= minY; y--) {
            for (int x = -extraBlocks; x < 16 + extraBlocks; x++) {
                for (int z = -extraBlocks; z < 16 + extraBlocks; z++) {
                    if (Material.ANCIENT_DEBRIS == chunk.getBlockType(x, y, z)) {
                        var pos = new Vec3i(x, y, z);
                        debris.put(pos, false);
                        // needs lava above tower
                        if (!(TYPES_EXPOSED.contains(chunk.getBlockType(x, y + 1, z)) || debris.getOrDefault(pos.up(), false))) {
                            continue;
                        }
                        for (int bottom = y - 1; bottom >= minY; bottom--) {
                            // moved down, found another debris, move down again
                            if (Material.ANCIENT_DEBRIS == chunk.getBlockType(x, bottom, z)) {
                                continue;
                            }
                            // have space below the entire stack
                            if (TYPES_SURROUND.contains(chunk.getBlockType(x, bottom, z))) {
                                debris.put(pos, true);
                            }
                            // other block interrupting
                            break;
                        }
                    }
                }
            }
        }
        return debris;
    }

    // returns all ancient debris after pushing them down
    public static Set<Vec3i> pushDown(IRegionAccess.Inbounds chunk, HashMap<Vec3i, Boolean> debris) {
        return debris.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparingInt(Vec3i::y)))
                .map(e -> {
                    if (!e.getValue()) {
                        return e.getKey();
                    }
                    var loc = e.getKey();
                    var current = chunk.getBlockType(loc.x(), loc.y(), loc.z());
                    var below = chunk.getBlockType(loc.x(), loc.y() - 1, loc.z());
                    chunk.setBlockType(loc.x(), loc.y(), loc.z(), below);
                    chunk.setBlockType(loc.x(), loc.y() - 1, loc.z(), current);
                    return loc.down();
                }).collect(Collectors.toSet());
    }

    public static List<Vec3i> hideExposed(IRegionAccess.Outbounds chunk, Set<Vec3i> debris) {
        return debris.stream().
                filter(block -> block.around().anyMatch(e -> TYPES_EXPOSED.contains(chunk.getBlockType(e))))
                .peek(block -> {
                    var copy = block.around()
                            .map(chunk::getBlockType)
                            .filter(TYPES_SURROUND::contains)
                            .findFirst()
                            .orElse(Material.NETHERRACK);
                    block.around()
                            .filter(e -> TYPES_EXPOSED.contains(chunk.getBlockType(e)))
                            .forEach(pos -> {
                                chunk.setBlockType(pos, copy);
                            });
                }).toList();
    }
}
