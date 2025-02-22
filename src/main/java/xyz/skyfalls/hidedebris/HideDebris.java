package xyz.skyfalls.hidedebris;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public final class HideDebris extends JavaPlugin implements Listener {
    private final Logger log = this.getLogger();
    // config is intentionally left null to prevent uninitialized usage
    private Config config;
    private WorldMigrator worldMigratorThread;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        parseConfig();
        if (config.worlds().isEmpty()) {
            log.warning("No worlds in \"worlds\", plugin disabled");
        }

        config.worlds.forEach(e -> {
            e.getPopulators().add(new PostprocessingPopulator(this.config.hideDebrisBelow));
        });

        if (config.migrateOnLoad) {
            Path cachePath = getDataFolder().toPath().toAbsolutePath();
            try {
                Files.createDirectories(cachePath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            worldMigratorThread = new WorldMigrator(this, this.config.hideDebrisBelow,
                    this.config.checkerThreadCount, this.config.worlds, cachePath,
                    this.config.batchSize, this.config.ticksPerBatch);
            worldMigratorThread.start();
        }
    }

    @Override
    public void onDisable() {
        if (worldMigratorThread != null) {
            worldMigratorThread.interrupt();
            log.info("Waiting for WorldMigrator thread to quit");
            for (int i = 0; ; i++) {
                if (!worldMigratorThread.isAlive()) {
                    break;
                }
                if (i > 40) {
                    // this might happen due to a bukkit issue causing a dead-lock in Chunk.isGenerated
                    // it does not happen for /reload, which also disable the plugin but doesn't unload the world
                    log.warning("WorldMigrator thread did not respond, force quitting");
                    worldMigratorThread.forceExit();
                    break;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        getServer().getWorlds().forEach(world -> {
            world.getPopulators().removeIf(e -> e instanceof PostprocessingPopulator);
        });
        this.config = null;
    }

    private record Config(List<World> worlds, int hideDebrisBelow, int checkerThreadCount, boolean migrateOnLoad,
                          int batchSize, int ticksPerBatch) {
    }

    private synchronized void parseConfig() {
        if (this.config != null) {
            return;
        }
        var worlds = getConfig().getStringList("worlds")
                .stream().map(s -> {
                    var optional = Optional.ofNullable(Bukkit.getServer().getWorld(s));
                    return optional.orElseThrow(() -> new RuntimeException("World " + s + " does not exist"));
                }).toList();
        if (worlds.isEmpty()) {
            log.warning("No worlds specified, plugin will be disabled");
        }
        var hideDebrisBelow = getConfig().getInt("hide-debris-below");
        if (hideDebrisBelow < worlds.get(0).getMinHeight()) {
            throw new RuntimeException("\"hide-debris-below\" is below minimum height of the world");
        }
        var checkerThreadCount = getConfig().getInt("checker-threads-count");
        if (checkerThreadCount < 1) {
            throw new RuntimeException("\"checker-threads-count\" should be at least 1");
        }
        var migrateOnLoad = getConfig().getBoolean("migrate-on-load");
        var batchSize = getConfig().getInt("batch-size");
        if (batchSize < 1) {
            throw new RuntimeException("\"batch-size\" should be at least 1");
        }
        var ticksPerBatch = getConfig().getInt("ticks-between-batches");
        config = new Config(worlds, hideDebrisBelow, checkerThreadCount, migrateOnLoad, batchSize, ticksPerBatch);
    }
}
