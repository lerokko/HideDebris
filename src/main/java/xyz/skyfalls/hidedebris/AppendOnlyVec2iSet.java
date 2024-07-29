package xyz.skyfalls.hidedebris;

import xyz.skyfalls.hidedebris.utils.Vec2i;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;

public class AppendOnlyVec2iSet extends LinkedHashSet<Vec2i> {
    private final Path path;
    private int alreadyWritten;
    private WritableByteChannel outChannel;

    public AppendOnlyVec2iSet(Path path) {
        this.path = path;
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException("this set is write only");
    }

    public void open() throws IOException {
        this.outChannel = Channels.newChannel(new BufferedOutputStream(
                Files.newOutputStream(this.path, StandardOpenOption.APPEND, StandardOpenOption.CREATE)));
        var channel = Channels.newChannel(new BufferedInputStream(Files.newInputStream(this.path)));
        ByteBuffer buf = ByteBuffer.allocate(8);
        while (channel.read(buf) != -1) {
            buf.flip();
            this.add(new Vec2i(buf.getInt(), buf.getInt()));
            buf.clear();
        }
        alreadyWritten = this.size();
    }

    public void writeAndFlush() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(8);
        var iterator = this.iterator();
        for (int i = 0; i < alreadyWritten; i++) {
            iterator.next();
        }
        while (iterator.hasNext()) {
            var cur = iterator.next();
            buf.putInt(cur.x());
            buf.putInt(cur.z());
            buf.flip();
            outChannel.write(buf);
            buf.clear();
        }
        alreadyWritten = this.size();
    }

    public void close() throws IOException {
        writeAndFlush();
        this.outChannel.close();
    }
}
