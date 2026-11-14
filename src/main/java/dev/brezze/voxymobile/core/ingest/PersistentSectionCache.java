package dev.brezze.voxymobile.core.ingest;

import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Versioned per-section disk cache. Call disk methods from a worker thread. */
public final class PersistentSectionCache {
    private static final int MAGIC = 0x564D5343;
    private static final int FORMAT_VERSION = 1;
    public static final int DEFAULT_MAX_FILES = 8192;
    public static final long DEFAULT_MAX_BYTES = 256L * 1024L * 1024L;
    private final Path directory;
    private final int maxFiles;
    private final long maxBytes;

    public PersistentSectionCache(Path root, String worldKey, String dimensionKey) {
        this(root, worldKey, dimensionKey, DEFAULT_MAX_FILES, DEFAULT_MAX_BYTES);
    }
    public PersistentSectionCache(Path root, String worldKey, String dimensionKey, int maxFiles, long maxBytes) {
        if (root == null || maxFiles < 1 || maxBytes < 1024) throw new IllegalArgumentException();
        directory = root.resolve(safeKey(worldKey)).resolve(safeKey(dimensionKey));
        this.maxFiles = maxFiles; this.maxBytes = maxBytes;
    }
    public Path directory() { return directory; }

    public synchronized boolean save(VoxelSection section) throws IOException {
        Files.createDirectories(directory);
        SectionPos p = section.position();
        Path target = fileFor(p), temp = Files.createTempFile(directory, "section-", ".tmp");
        boolean moved = false;
        try {
            try (OutputStream raw = Files.newOutputStream(temp);
                 GZIPOutputStream gzip = new GZIPOutputStream(raw);
                 DataOutputStream out = new DataOutputStream(gzip)) {
                out.writeInt(MAGIC); out.writeInt(FORMAT_VERSION);
                out.writeInt(p.x()); out.writeInt(p.y()); out.writeInt(p.z());
                int[] palette = section.paletteCopy(); short[] indices = section.indicesCopy();
                out.writeInt(palette.length);
                for (int id : palette) out.writeInt(id);
                for (short index : indices) out.writeShort(index);
            }
            try { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
            moved = true; enforceBudget(target); return true;
        } finally { if (!moved) Files.deleteIfExists(temp); }
    }

    public synchronized int loadAll(ChunkSnapshotStore store, int maxToLoad) throws IOException {
        return loadAll(store, maxToLoad, 0.0, 0.0, 0.0);
    }

    /** Restore the nearest cached sections first, using block-space AABB distance. */
    public synchronized int loadAll(ChunkSnapshotStore store, int maxToLoad,
                                    double centerX, double centerY, double centerZ) throws IOException {
        if (store == null || maxToLoad < 0 || !Double.isFinite(centerX)
                || !Double.isFinite(centerY) || !Double.isFinite(centerZ))
            throw new IllegalArgumentException();
        if (!Files.isDirectory(directory) || maxToLoad == 0) return 0;
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.vms")) {
            for (Path file : stream) files.add(file);
        }
        files.sort(Comparator
                .comparingDouble((Path file) -> distanceSquared(file, centerX, centerY, centerZ))
                .thenComparing(Comparator.comparingLong(PersistentSectionCache::modified).reversed()));
        int loaded = 0;
        for (Path file : files) {
            if (loaded >= maxToLoad) break;
            try { VoxelSection section = read(file); if (section != null) { store.put(section); loaded++; } }
            catch (IOException | RuntimeException ignored) { /* corrupt entries are skipped */ }
        }
        return loaded;
    }

    private static double distanceSquared(Path file, double x, double y, double z) {
        String name = file.getFileName().toString();
        if (!name.endsWith(".vms")) return Double.POSITIVE_INFINITY;
        String[] coordinates = name.substring(0, name.length() - 4).split("_");
        if (coordinates.length != 3) return Double.POSITIVE_INFINITY;
        try {
            double minX = Integer.parseInt(coordinates[0]) * 16.0;
            double minY = Integer.parseInt(coordinates[1]) * 16.0;
            double minZ = Integer.parseInt(coordinates[2]) * 16.0;
            double dx = axisDistance(x, minX, minX + 16.0);
            double dy = axisDistance(y, minY, minY + 16.0);
            double dz = axisDistance(z, minZ, minZ + 16.0);
            return dx * dx + dy * dy + dz * dz;
        } catch (NumberFormatException ignored) {
            return Double.POSITIVE_INFINITY;
        }
    }

    private static double axisDistance(double point, double min, double max) {
        return point < min ? min - point : point > max ? point - max : 0.0;
    }

    public synchronized boolean remove(SectionPos position) throws IOException { return Files.deleteIfExists(fileFor(position)); }
    public synchronized int fileCount() throws IOException {
        if (!Files.isDirectory(directory)) return 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.vms")) {
            int count = 0; for (Path ignored : stream) count++; return count;
        }
    }

    private VoxelSection read(Path file) throws IOException {
        try (InputStream raw = Files.newInputStream(file); GZIPInputStream gzip = new GZIPInputStream(raw);
             DataInputStream in = new DataInputStream(gzip)) {
            if (in.readInt() != MAGIC || in.readInt() != FORMAT_VERSION) return null;
            SectionPos pos = new SectionPos(in.readInt(), in.readInt(), in.readInt());
            int size = in.readInt(); if (size < 1 || size > 65536) return null;
            int[] palette = new int[size]; for (int i=0;i<size;i++) palette[i]=in.readInt();
            short[] indices = new short[VoxelSection.VOLUME]; for (int i=0;i<indices.length;i++) indices[i]=in.readShort();
            if (in.read() != -1) return null;
            return new VoxelSection(pos, palette, indices);
        } catch (EOFException truncated) { return null; }
    }
    private Path fileFor(SectionPos p) { return directory.resolve(p.x()+"_"+p.y()+"_"+p.z()+".vms"); }
    private void enforceBudget(Path newest) throws IOException {
        List<Path> files = new ArrayList<>(); long total=0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.vms")) {
            for (Path f : stream) { files.add(f); total += Files.size(f); }
        }
        files.sort(Comparator.comparingLong(PersistentSectionCache::modified));
        while ((files.size()>maxFiles || total>maxBytes) && !files.isEmpty()) {
            Path oldest=files.remove(0); if (oldest.equals(newest) && files.isEmpty()) break;
            long size=Files.size(oldest); Files.deleteIfExists(oldest); total-=size;
        }
    }
    private static long modified(Path p) {
        try { return Files.getLastModifiedTime(p).toMillis(); } catch (IOException e) { return Long.MIN_VALUE; }
    }
    private static String safeKey(String value) {
        if (value==null || value.isBlank()) throw new IllegalArgumentException("cache namespace required");
        String safe=value.replaceAll("[^A-Za-z0-9._-]","_");
        return safe.substring(0,Math.min(80,safe.length()));
    }
}
