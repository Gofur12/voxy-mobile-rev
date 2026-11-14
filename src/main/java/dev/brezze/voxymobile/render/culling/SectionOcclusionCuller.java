package dev.brezze.voxymobile.render.culling;

import dev.brezze.voxymobile.core.voxel.SectionPos;
import dev.brezze.voxymobile.core.voxel.VoxelSection;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Conservative section-level occlusion culler.
 * A section is rejected only when all six adjacent sections have a completely
 * occupied boundary face facing it. The current mesh pipeline renders every
 * non-air palette entry as opaque (alpha=1), which is the assumption used here.
 * If transparent materials are introduced, this culler must be given opacity
 * metadata instead of treating every non-air block as an occluder.
 */
public final class SectionOcclusionCuller {
    private static final int NEIGHBOR_COUNT = 6;
    private final Map<SectionPos, CacheEntry> cache = new HashMap<>();

    /** Returns true only when the section is enclosed by six fully covered neighbor faces. */
    public boolean isOccluded(VoxelSection section, Map<SectionPos, VoxelSection> sections) {
        if (section == null || sections == null) return false;
        SectionPos p = section.position();
        VoxelSection[] neighbors = neighbors(p, sections);
        CacheEntry previous = cache.get(p);
        if (previous != null && previous.matches(section, neighbors)) return previous.occluded;

        boolean occluded = true;
        for (int i = 0; i < NEIGHBOR_COUNT; i++) {
            if (neighbors[i] == null || !hasFullFacingBoundary(neighbors[i], i ^ 1)) {
                occluded = false;
                break;
            }
        }
        cache.put(p, new CacheEntry(section, neighbors, occluded));
        return occluded;
    }

    /** Drop cache entries for sections no longer present in the snapshot store. */
    public void retain(Set<SectionPos> liveSections) {
        cache.keySet().retainAll(liveSections);
    }

    public void clear() { cache.clear(); }
    public int cachedSectionCount() { return cache.size(); }

    private static VoxelSection[] neighbors(SectionPos p, Map<SectionPos, VoxelSection> sections) {
        return new VoxelSection[] {
                sections.get(new SectionPos(p.x() - 1, p.y(), p.z())),
                sections.get(new SectionPos(p.x() + 1, p.y(), p.z())),
                sections.get(new SectionPos(p.x(), p.y() - 1, p.z())),
                sections.get(new SectionPos(p.x(), p.y() + 1, p.z())),
                sections.get(new SectionPos(p.x(), p.y(), p.z() - 1)),
                sections.get(new SectionPos(p.x(), p.y(), p.z() + 1))
        };
    }

    /** face order follows GreedyLodMesher: -X,+X,-Y,+Y,-Z,+Z. */
    private static boolean hasFullFacingBoundary(VoxelSection section, int face) {
        for (int v = 0; v < VoxelSection.EDGE; v++) {
            for (int u = 0; u < VoxelSection.EDGE; u++) {
                int id = switch (face) {
                    case 0 -> section.blockId(0, v, u);
                    case 1 -> section.blockId(15, v, u);
                    case 2 -> section.blockId(u, 0, v);
                    case 3 -> section.blockId(u, 15, v);
                    case 4 -> section.blockId(u, v, 0);
                    case 5 -> section.blockId(u, v, 15);
                    default -> throw new IllegalArgumentException("face must be 0..5");
                };
                if (id == 0) return false;
            }
        }
        return true;
    }

    private static final class CacheEntry {
        private final VoxelSection source;
        private final VoxelSection[] neighbors;
        private final boolean occluded;

        private CacheEntry(VoxelSection source, VoxelSection[] neighbors, boolean occluded) {
            this.source = source;
            this.neighbors = neighbors.clone();
            this.occluded = occluded;
        }

        private boolean matches(VoxelSection source, VoxelSection[] currentNeighbors) {
            if (this.source != source) return false;
            for (int i = 0; i < NEIGHBOR_COUNT; i++)
                if (neighbors[i] != currentNeighbors[i]) return false;
            return true;
        }
    }
}
