package dev.brezze.voxymobile.render.geometry;

/** CPU contract test for per-pair source revision tracking and duplicate upload suppression. */
public final class LodSeamRevisionTrackerSmokeTest {
    private static int assertions;
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    private static void bad(Runnable action) {
        assertions++;
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("expected IllegalArgumentException");
    }
    public static void main(String[] args) {
        LodSeamRevisionTracker tracker = new LodSeamRevisionTracker();
        LodSeamKey a = new LodSeamKey(1, 2);
        LodSeamKey b = new LodSeamKey(1, 3);
        long first = tracker.revisionFor(a, 4, 9);
        check(first == 1, "first pair revision starts at one");
        check(tracker.revisionFor(a, 4, 9) == first, "unchanged source pair keeps revision");
        long second = tracker.revisionFor(a, 5, 9);
        check(second == first + 1, "fine mesh revision advances upload revision");
        long third = tracker.revisionFor(a, 5, 10);
        check(third == second + 1, "coarse mesh revision advances upload revision");
        check(tracker.revisionFor(b, 5, 10) == 1, "different seam key has independent revision");
        check(tracker.currentRevision(a) == third, "current revision is queryable");
        check(tracker.remove(a), "active pair state can be removed");
        check(tracker.currentRevision(a) == -1, "removed pair has no current revision");
        check(tracker.revisionFor(a, 5, 10) == 1, "re-added pair starts a fresh lifecycle");
        check(tracker.size() == 2, "tracker retains only active lifecycle states");
        bad(() -> tracker.revisionFor(null, 0, 0));
        bad(() -> tracker.revisionFor(a, -1, 0));
        System.out.println("LodSeamRevisionTrackerSmokeTest PASS (12 assertions)");
    }
}
