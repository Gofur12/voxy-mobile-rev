package dev.brezze.voxymobile.render.geometry;

/** CPU-only per-frame GLES upload byte budget shared by leaf and parent mesh uploads. */
public final class GpuUploadBudgetController {
    public static final long DEFAULT_BYTES_PER_FRAME = 2L * 1024L * 1024L;
    private static final long MIN_BYTES_PER_FRAME = 512L * 1024L;
    private long frameBudgetBytes = DEFAULT_BYTES_PER_FRAME;
    private long uploadedBytesThisFrame;

    /** Starts a new render-frame budget; higher GPU-memory pressure lowers upload throughput. */
    public synchronized void beginFrame(long allocatedGpuBytes, int memoryPressureLevel) {
        if (allocatedGpuBytes < 0) throw new IllegalArgumentException("allocatedGpuBytes must be non-negative");
        if (memoryPressureLevel < 0 || memoryPressureLevel > 3)
            throw new IllegalArgumentException("memoryPressureLevel must be 0..3");
        frameBudgetBytes = switch (memoryPressureLevel) {
            case 0 -> DEFAULT_BYTES_PER_FRAME;
            case 1 -> DEFAULT_BYTES_PER_FRAME * 3L / 4L;
            case 2 -> DEFAULT_BYTES_PER_FRAME / 2L;
            default -> MIN_BYTES_PER_FRAME;
        };
        uploadedBytesThisFrame = 0L;
    }

    /**
     * Reserves bytes before issuing GL buffer uploads. One oversized mesh is allowed on an
     * otherwise empty frame so a large mesh cannot starve forever; no further upload follows it.
     */
    public synchronized boolean tryConsume(long payloadBytes) {
        if (payloadBytes < 0) throw new IllegalArgumentException("payloadBytes must be non-negative");
        if (payloadBytes == 0) return true;
        if (uploadedBytesThisFrame == 0L && payloadBytes > frameBudgetBytes) {
            uploadedBytesThisFrame = payloadBytes;
            return true;
        }
        if (payloadBytes > frameBudgetBytes - uploadedBytesThisFrame) return false;
        uploadedBytesThisFrame += payloadBytes;
        return true;
    }

    public synchronized long frameBudgetBytes() { return frameBudgetBytes; }
    public synchronized long uploadedBytesThisFrame() { return uploadedBytesThisFrame; }
    public synchronized long remainingBytesThisFrame() {
        return Math.max(0L, frameBudgetBytes - uploadedBytesThisFrame);
    }
}
