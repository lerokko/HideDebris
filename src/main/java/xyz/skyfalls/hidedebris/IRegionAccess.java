package xyz.skyfalls.hidedebris;

import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.LimitedRegion;
import xyz.skyfalls.hidedebris.utils.Vec3i;

import javax.annotation.Nullable;

public interface IRegionAccess {
    Material getBlockType(int x, int y, int z);

    void setBlockType(int x, int y, int z, Material material);

    default Material getBlockType(Vec3i pos) {
        return getBlockType(pos.x(), pos.y(), pos.z());
    }

    default void setBlockType(Vec3i pos, Material material) {
        setBlockType(pos.x(), pos.y(), pos.z(), material);
    }

    interface Inbounds extends IRegionAccess {
    }

    interface Outbounds extends IRegionAccess {
        // outbounds get may return null, and set may do nothing if the target chunk hasn't been generated
        @Nullable
        Material getBlockType(int x, int y, int z);

        @Nullable
        default Material getBlockType(Vec3i pos) {
            return getBlockType(pos.x(), pos.y(), pos.z());
        }
    }

    // WorldAdaptor implements outbound block access in generated chunks surrounding the center chunk
    class WorldAdaptor implements Inbounds, Outbounds {
        private static final Boolean[][] isChunkGeneratedCache = new Boolean[3][3];
        private final World world;
        private final int centerChunkX;
        private final int centerChunkZ;

        public WorldAdaptor(World world, int centerChunkX, int centerChunkZ) {
            this.world = world;
            this.centerChunkX = centerChunkX;
            this.centerChunkZ = centerChunkZ;
        }

        private Chunk getChunk(int x, int z) {
            return world.getChunkAt(centerChunkX + (x >> 4), centerChunkZ + (z >> 4), false);
        }

        private boolean isChunkGenerated(int x, int z) {
            x = (x + 16) / 16;
            z = (z + 16) / 16;
            if (isChunkGeneratedCache[x][z] != null) {
                return isChunkGeneratedCache[x][z];
            }
            return isChunkGeneratedCache[x][z] = getChunk(x, z).isGenerated();
        }

        @Override
        public @Nullable Material getBlockType(int x, int y, int z) {
            if (!isChunkGenerated(x, z)) {
                return null;
            }
            return this.world.getType(centerChunkX * 16 + x, y, centerChunkZ * 16 + z);
        }

        @Override
        public void setBlockType(int x, int y, int z, Material material) {
            if (isChunkGenerated(x, z)) {
                this.world.setType(centerChunkX * 16 + x, y, centerChunkZ * 16 + z, material);
            }
        }
    }

    static WorldAdaptor from(World world, int centerChunkX, int centerChunkZ) {
        return new WorldAdaptor(world, centerChunkX, centerChunkZ);
    }

    class ChunkSnapshotAdaptor implements Inbounds {
        private final ChunkSnapshot chunk;

        public ChunkSnapshotAdaptor(ChunkSnapshot chunk) {
            this.chunk = chunk;
        }

        @Override
        public Material getBlockType(int x, int y, int z) {
            return chunk.getBlockType(x, y, z);
        }

        @Override
        public void setBlockType(int x, int y, int z, Material material) {
            throw new UnsupportedOperationException("snapshot is read only");
        }
    }

    static ChunkSnapshotAdaptor from(ChunkSnapshot snapshot) {
        return new ChunkSnapshotAdaptor(snapshot);
    }

    class LimitedRegionAdaptor implements Inbounds, Outbounds {
        private final LimitedRegion chunk;
        private final int chunkX;
        private final int chunkZ;

        public LimitedRegionAdaptor(LimitedRegion chunk, int chunkX, int chunkZ) {
            if (chunk.getBuffer() < 16) {
                throw new IllegalArgumentException("LimitedRegion should extend 1 chunk outwards");
            }
            this.chunk = chunk;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        @Override
        public Material getBlockType(int x, int y, int z) {
            return chunk.getType(chunkX * 16 + x, y, chunkZ * 16 + z);
        }

        @Override
        public void setBlockType(int x, int y, int z, Material material) {
            chunk.setType(chunkX * 16 + x, y, chunkZ * 16 + z, material);
        }
    }

    static LimitedRegionAdaptor from(LimitedRegion region, int chunkX, int chunkZ) {
        return new LimitedRegionAdaptor(region, chunkX, chunkZ);
    }

    class ChunkAdaptor implements Inbounds {
        private final Chunk chunk;

        public ChunkAdaptor(Chunk chunk) {
            this.chunk = chunk;
        }

        @Override
        public Material getBlockType(int x, int y, int z) {
            return chunk.getBlock(x, y, z).getType();
        }

        @Override
        public void setBlockType(int x, int y, int z, Material material) {
            chunk.getBlock(x, y, z).setType(material, false);
        }
    }

    static ChunkAdaptor from(Chunk chunk) {
        return new ChunkAdaptor(chunk);
    }
}
