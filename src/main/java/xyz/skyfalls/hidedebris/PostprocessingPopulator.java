package xyz.skyfalls.hidedebris;

import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import javax.annotation.Nonnull;
import java.util.Random;

public class PostprocessingPopulator extends BlockPopulator {
    private final int maxY;

    public PostprocessingPopulator(int maxY) {
        this.maxY = maxY;
    }

    @Override
    public void populate(@Nonnull WorldInfo worldInfo, @Nonnull Random random, int chunkX, int chunkZ, @Nonnull LimitedRegion limitedRegion) {
        var chunk = IRegionAccess.from(limitedRegion, chunkX, chunkZ);
        var debris = ChunkProcessor.scanDebris(chunk, worldInfo.getMinHeight(), maxY, 3);
        var debris2 = ChunkProcessor.pushDown(chunk, debris);
        ChunkProcessor.hideExposed(chunk, debris2);
    }
}
