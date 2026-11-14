package dev.brezze.voxymobile.render.geometry;

public final class GpuMeshEvictionPolicySmokeTest {
    public static void main(String[] args) {
        double sameDistance = 4096.0;
        double leaf = GpuMeshEvictionPolicy.score(sameDistance, 0);
        double parent = GpuMeshEvictionPolicy.score(sameDistance, 1);
        double coarseParent = GpuMeshEvictionPolicy.score(sameDistance, 4);
        check(leaf > parent && parent > coarseParent,
                "fine meshes are evicted before coarse coverage at equal distance");
        check(GpuMeshEvictionPolicy.score(16384.0, 1) > leaf,
                "a much farther parent can still be evicted before a near leaf");
        check(GpuMeshEvictionPolicy.score(0.0, 0) == 0.0, "camera-local mesh has zero eviction score");
        check(GpuMeshEvictionPolicy.score(-1.0, 0) < 0.0, "negative distance rejected");
        check(GpuMeshEvictionPolicy.score(1.0, 21) < 0.0, "unsupported level rejected");
        System.out.println("GpuMeshEvictionPolicySmokeTest: 5 assertions passed");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
