package xyz.skyfalls.hidedebris.utils;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LogFile {
    private final WritableByteChannel outChannel;

    public LogFile(Path logFolder, Vec2i region) throws IOException {
        this.outChannel = Channels.newChannel(new BufferedOutputStream(
                Files.newOutputStream(logFolder.resolve("r.%d.%d.log".formatted(region.x(), region.z())),
                        StandardOpenOption.APPEND, StandardOpenOption.CREATE)));
        log("Processing region %d, %d", region.x(), region.z());
    }

    public void log(String s, Object... args) throws IOException {
        s = (s + "\n").formatted(args);
        var buf = ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8));
        this.outChannel.write(buf);
    }

    public void logChunkPushed(Vec2i chunk, HashMap<Vec3i, Boolean> debris) throws IOException {
        for (Map.Entry<Vec3i, Boolean> e : debris.entrySet()) {
            if (e.getValue()) {
                var pos = e.getKey();
                log("push %d, %d, %d", chunk.x() * 16 + pos.x(), pos.y(), chunk.z() * 16 + pos.z());
            }
        }

    }

    public void logChunkHidden(Vec2i chunk, List<Vec3i> debris) throws IOException {
        for (Vec3i e : debris) {
            log("hide %d, %d, %d", chunk.x() * 16 + e.x(), e.y(), chunk.z() * 16 + e.z());
        }
    }

    public void flushAndClose() throws IOException {
        this.outChannel.close();
    }
}
