package dev.brezze.voxymobile.render;

/** Per-frame accounting for the terrain draw decision pipeline. */
public final class RenderFrameAudit {
    private int candidates, frustumRejected, missingMeshes, staleParents, leafDraws, parentDraws;
    public void candidate() { candidates++; }
    public void frustumRejected() { frustumRejected++; }
    public void missingMesh() { missingMeshes++; }
    public void staleParent() { staleParents++; }
    public void leafDrawn() { leafDraws++; }
    public void parentDrawn() { parentDraws++; }
    public int candidates() { return candidates; }
    public int frustumRejectedCount() { return frustumRejected; }
    public int missingMeshes() { return missingMeshes; }
    public int staleParents() { return staleParents; }
    public int leafDraws() { return leafDraws; }
    public int parentDraws() { return parentDraws; }
    public int totalDraws() { return leafDraws + parentDraws; }
}
