package dev.brezze.voxymobile.render.geometry;

import java.util.Arrays;

/** Deterministic nearest-first seam work selection with a hard per-refresh cap. */
public final class LodSeamWorkScheduler {
    private LodSeamWorkScheduler() {}

    public static int[] prioritize(double[] distanceSquared, int maxItems) {
        if (distanceSquared == null) throw new IllegalArgumentException("distances are null");
        if (maxItems < 0) throw new IllegalArgumentException("maxItems must be non-negative");
        Integer[] order = new Integer[distanceSquared.length];
        for (int i = 0; i < order.length; i++) {
            double distance = distanceSquared[i];
            if (Double.isNaN(distance) || distance < 0.0)
                throw new IllegalArgumentException("distance must be non-negative and not NaN");
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> {
            int compared = Double.compare(distanceSquared[a], distanceSquared[b]);
            return compared != 0 ? compared : Integer.compare(a, b);
        });
        int count = Math.min(maxItems, order.length);
        int[] selected = new int[count];
        for (int i = 0; i < count; i++) selected[i] = order[i];
        return selected;
    }
}
