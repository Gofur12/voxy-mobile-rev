package dev.brezze.voxymobile.render.culling;

import dev.brezze.voxymobile.core.voxel.SectionPos;

public final class CameraRelativeAabbSmokeTest {
    public static void main(String[] args) {
        CameraRelativeAabb section = CameraRelativeAabb.section(new SectionPos(2, -1, 3), 40.5, -4.0, 55.25);
        near(section.minX(), -8.5f, "section min X");
        near(section.minY(), -12.0f, "section min Y");
        near(section.minZ(), -7.25f, "section min Z");
        near(section.maxX(), 7.5f, "section max X");
        near(section.maxY(), 4.0f, "section max Y");
        near(section.maxZ(), 8.75f, "section max Z");

        CameraRelativeAabb parent = CameraRelativeAabb.node(new SectionPos(-2, 1, -3), 8, -20, 8, -40);
        near(parent.minX(), -12.0f, "parent min X");
        near(parent.minY(), 8.0f, "parent min Y");
        near(parent.minZ(), -8.0f, "parent min Z");
        near(parent.maxX(), 116.0f, "parent max X");
        near(parent.maxY(), 136.0f, "parent max Y");
        near(parent.maxZ(), 120.0f, "parent max Z");

        require(!section.isVisible(null), "null frustum must be rejected safely");
        boolean rejected = false;
        try { CameraRelativeAabb.node(new SectionPos(0,0,0), 0, 0,0,0); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "zero parent span must be rejected");
        System.out.println("CameraRelativeAabbSmokeTest: PASS (14 assertions)");
    }
    private static void near(float actual, float expected, String label) {
        if (Math.abs(actual-expected) > 1.0e-5f) throw new AssertionError(label+": expected "+expected+", got "+actual);
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
