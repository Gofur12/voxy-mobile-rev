package dev.brezze.voxymobile.core.ingest;

import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Builds an immutable palette-compressed section from 4096 raw block-state IDs. */
public final class SectionSnapshotBuilder {
    private SectionSnapshotBuilder() {}

    public static VoxelSection build(SectionPos position, int[] rawIds) {
        if (position == null || rawIds == null || rawIds.length != VoxelSection.VOLUME)
            throw new IllegalArgumentException("Expected exactly 4096 block state IDs");
        Map<Integer, Short> paletteMap = new LinkedHashMap<>();
        int[] paletteTmp = new int[Math.min(rawIds.length, 256)];
        short[] indices = new short[rawIds.length];
        int paletteSize = 0;
        for (int i = 0; i < rawIds.length; i++) {
            int raw = rawIds[i];
            Short index = paletteMap.get(raw);
            if (index == null) {
                if (paletteSize == Short.MAX_VALUE) throw new IllegalArgumentException("Too many unique block states");
                if (paletteSize == paletteTmp.length) paletteTmp = Arrays.copyOf(paletteTmp, Math.min(Short.MAX_VALUE, paletteTmp.length * 2));
                index = (short) paletteSize;
                paletteMap.put(raw, index);
                paletteTmp[paletteSize++] = raw;
            }
            indices[i] = index;
        }
        // Reserve local palette index 0 for air, regardless of traversal order.
        Short air = paletteMap.get(0);
        if (air == null) {
            int[] expanded = new int[paletteSize + 1];
            expanded[0] = 0;
            System.arraycopy(paletteTmp, 0, expanded, 1, paletteSize);
            for (int i = 0; i < indices.length; i++) indices[i] = (short) (Short.toUnsignedInt(indices[i]) + 1);
            return new VoxelSection(position, expanded, indices);
        }
        int airIndex = Short.toUnsignedInt(air);
        if (airIndex != 0) {
            int oldZero = paletteTmp[0]; paletteTmp[0] = 0; paletteTmp[airIndex] = oldZero;
            for (int i = 0; i < indices.length; i++) {
                int v = Short.toUnsignedInt(indices[i]);
                if (v == 0) indices[i] = (short) airIndex;
                else if (v == airIndex) indices[i] = 0;
            }
        }
        return new VoxelSection(position, Arrays.copyOf(paletteTmp, paletteSize), indices);
    }
}
