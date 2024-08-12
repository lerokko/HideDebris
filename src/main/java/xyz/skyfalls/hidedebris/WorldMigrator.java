package xyz.skyfalls.hidedebris;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import xyz.skyfalls.hidedebris.utils.LogFile;
import xyz.skyfalls.hidedebris.utils.RegionUtils;
import xyz.skyfalls.hidedebris.utils.Tuple3;
import xyz.skyfalls.hidedebris.utils.Vec2i;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class WorldMigrator extends Thread {
    private final Logger log;
    private final HideDebris plugin;
    private final int hideDebrisBelow;
    private final ExecutorService pool;
    private final List<World> worlds;
    private final Path cache;
    private final int batchSize;
    private final int ticksPerBatch;
    private AppendOnlyVec2iSet regionsMigrated;

    public WorldMigrator(HideDebris plugin, int hideDebrisBelow, int checkerThreadCount,
                         List<World> worlds, Path cache, int batchSize, int ticksPerBatch) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.hideDebrisBelow = hideDebrisBelow;
        this.pool = Executors.newWorkStealingPool(checkerThreadCount);
        this.worlds = worlds;
        this.cache = cache;
        this.batchSize = batchSize;
        this.ticksPerBatch = ticksPerBatch;
    }

    @Override
    public void run() {
        for (World e : worlds) {
            log.info("Starting background migration for world " + e.getName());
            try {
                this.migrateWorld(e);
            } catch (IOException ex) {
                log.log(Level.WARNING, "World migration failed due to error", ex);
                break;
            }
            if (this.isInterrupted()) {
                return;
            }
        }
        log.info("All worlds have been migrated");
    }

    public void forceExit() {
        try {
            regionsMigrated.writeAndFlush();
            regionsMigrated.close();
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to persist process file", e);
        }
        log.info("Progress file saved");
    }

    private void migrateWorld(World world) throws IOException {
        this.regionsMigrated = new AppendOnlyVec2iSet(cache.resolve(world.getName() + ".regions"));
        regionsMigrated.open();
        List<Vec2i> regions;
        try {
            regions = RegionUtils.getRegions(world);
        } catch (IOException e) {
            log.warning("Failed to list region files in world " + world.getName());
            throw e;
        }
        log.info("Starting migration of world %s, %d region files total".formatted(world.getName(), regions.size()));
        int regionsMigratedThisRun = 0;
        Path logFolder = cache.resolve("logs");
        Files.createDirectories(logFolder);
        for (int i = 0; i < regions.size(); i++) {
            Vec2i region = regions.get(i);
            if (regionsMigrated.contains(region)) {
                continue;
            }
            LogFile changeLog = new LogFile(logFolder, region);
            var chunks = RegionUtils.getChunksInRegion(region)
                    .map(e -> world.getChunkAt(e.x(), e.z(), false))
                    .filter(Chunk::isGenerated)
                    .map(chunk -> CompletableFuture.supplyAsync(() -> {
                        // avoid accessing Chunk data in a async task
                        var snapshot = chunk.getChunkSnapshot();
                        return new Tuple3<>(chunk,
                                ChunkProcessor.scanDebris(IRegionAccess.from(snapshot), world.getMinHeight(), this.hideDebrisBelow),
                                new Vec2i(chunk.getX(), chunk.getZ()));
                    }, pool))
                    .map(CompletableFuture::join).toList();
            changeLog.log("totalChunks=%d", chunks.size());

            CountDownLatch latch = new CountDownLatch(chunks.size());
            AtomicInteger counter = new AtomicInteger();
            var map = chunks.stream()
                    .collect(Collectors.groupingBy(x -> counter.getAndIncrement() / batchSize));
            map.forEach((key, value) -> Bukkit.getScheduler().runTaskLater(plugin, () -> {
                value.forEach(e -> {
                    var debrisMarked = e.b();
                    var chunk = e.a();
                    var access = IRegionAccess.from(world, chunk.getX(), chunk.getZ());
                    var allDebris = ChunkProcessor.pushDown(access, debrisMarked);
                    var hidden = ChunkProcessor.hideExposed(access, allDebris);
                    try {
                        // hope that the buffer is big enough or this may stall
                        changeLog.logChunkPushed(e.c(), debrisMarked);
                        changeLog.logChunkHidden(e.c(), hidden);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                    chunk.unload();
                    latch.countDown();
                });
            }, (long) key * ticksPerBatch));
            try {
                latch.await();
            } catch (InterruptedException e) {
                log.warning("WorldMigrator interrupted, stopping migration");
                pool.shutdownNow();
                break;
            }
            regionsMigratedThisRun++;
            regionsMigrated.add(region);
            regionsMigrated.writeAndFlush();
            changeLog.log("Marked region as finished");
            changeLog.flushAndClose();
            log.info("Region %d, %d migrated, totalChunks=%d, progress=%d/%d"
                    .formatted(region.x(), region.y(), chunks.size(), regionsMigratedThisRun, regions.size()));
        }
        regionsMigrated.writeAndFlush();
        regionsMigrated.close();
        log.info("Finished migrating world %s".formatted(world.getName()));
    }

    public void close() {
        pool.shutdown();
    }
}
