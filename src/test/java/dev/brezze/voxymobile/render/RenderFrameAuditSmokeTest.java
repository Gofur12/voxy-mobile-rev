package dev.brezze.voxymobile.render;

public final class RenderFrameAuditSmokeTest {
    public static void main(String[] args) {
        RenderFrameAudit audit = new RenderFrameAudit();
        audit.candidate(); audit.candidate(); audit.candidate(); audit.frustumRejected();
        audit.missingMesh(); audit.staleParent(); audit.leafDrawn(); audit.parentDrawn();
        check(audit.candidates() == 3, "candidate count");
        check(audit.frustumRejectedCount() == 1, "frustum rejection count");
        check(audit.missingMeshes() == 1, "missing mesh count");
        check(audit.staleParents() == 1, "stale parent count");
        check(audit.leafDraws() == 1 && audit.parentDraws() == 1, "draw type counts");
        check(audit.totalDraws() == 2, "total draw count");
        System.out.println("RenderFrameAuditSmokeTest: 6 assertions passed");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
