package dev.brezze.voxymobile.render.culling;

import org.joml.Matrix4f;

public final class FrustumCullerSmokeTest {
    public static void main(String[] args) {
        FrustumCuller culler = FrustumCuller.from(new Matrix4f().identity());
        require(culler.isVisible(-0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f), "inside box rejected");
        require(!culler.isVisible(2f, -0.5f, -0.5f, 3f, 0.5f, 0.5f), "right outside box accepted");
        require(!culler.isVisible(-3f, -0.5f, -0.5f, -2f, 0.5f, 0.5f), "left outside box accepted");
        require(!culler.isVisible(-0.5f, 2f, -0.5f, 0.5f, 3f, 0.5f), "top outside box accepted");
        require(!culler.isVisible(-0.5f, -0.5f, 2f, 0.5f, 0.5f, 3f), "far outside box accepted");
        System.out.println("FrustumCullerSmokeTest: PASS");
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
