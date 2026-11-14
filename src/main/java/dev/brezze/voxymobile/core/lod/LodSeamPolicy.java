package dev.brezze.voxymobile.core.lod;

/** Conservative neighbor-LOD constraint; larger levels represent coarser terrain. */
public final class LodSeamPolicy {
    public static final int MAX_ADJACENT_LEVEL_DELTA = 1;
    private LodSeamPolicy() {}

    /** Clamp a requested LOD to at most one level away from its adjacent region. */
    public static int constrainAdjacentLevel(int requestedLevel, int adjacentLevel) {
        validate(requestedLevel, adjacentLevel);
        int min = Math.max(0, adjacentLevel - MAX_ADJACENT_LEVEL_DELTA);
        int max = adjacentLevel + MAX_ADJACENT_LEVEL_DELTA;
        return Math.max(min, Math.min(max, requestedLevel));
    }

    public static boolean hasSeamRisk(int firstLevel, int secondLevel) {
        validate(firstLevel, secondLevel);
        return Math.abs(firstLevel - secondLevel) > MAX_ADJACENT_LEVEL_DELTA;
    }

    public static int requiredIntermediateLevels(int firstLevel, int secondLevel) {
        validate(firstLevel, secondLevel);
        return Math.max(0, Math.abs(firstLevel - secondLevel) - 1);
    }

    private static void validate(int a, int b) {
        if (a < 0 || b < 0) throw new IllegalArgumentException("LOD levels must be non-negative");
    }
}
