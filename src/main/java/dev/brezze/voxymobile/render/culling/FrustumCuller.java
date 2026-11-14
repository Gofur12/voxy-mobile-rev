package dev.brezze.voxymobile.render.culling;

import org.joml.Matrix4fc;

/** Conservative six-plane frustum test for axis-aligned boxes in the same space as the clip matrix. */
public final class FrustumCuller {
    private final Plane[] planes;

    private FrustumCuller(Plane[] planes) { this.planes = planes; }

    /** Extracts OpenGL clip planes from a column-major JOML projection-view matrix. */
    public static FrustumCuller from(Matrix4fc m) {
        return new FrustumCuller(new Plane[] {
                plane(m.m03() + m.m00(), m.m13() + m.m10(), m.m23() + m.m20(), m.m33() + m.m30()),
                plane(m.m03() - m.m00(), m.m13() - m.m10(), m.m23() - m.m20(), m.m33() - m.m30()),
                plane(m.m03() + m.m01(), m.m13() + m.m11(), m.m23() + m.m21(), m.m33() + m.m31()),
                plane(m.m03() - m.m01(), m.m13() - m.m11(), m.m23() - m.m21(), m.m33() - m.m31()),
                plane(m.m03() + m.m02(), m.m13() + m.m12(), m.m23() + m.m22(), m.m33() + m.m32()),
                plane(m.m03() - m.m02(), m.m13() - m.m12(), m.m23() - m.m22(), m.m33() - m.m32())
        });
    }

    private static Plane plane(float a, float b, float c, float d) {
        float length = (float) Math.sqrt(a * a + b * b + c * c);
        if (length < 1.0e-7f) return new Plane(0, 0, 0, Float.POSITIVE_INFINITY);
        float inv = 1.0f / length;
        return new Plane(a * inv, b * inv, c * inv, d * inv);
    }

    /** Bounds are expressed in the clip matrix's input coordinate space. */
    public boolean isVisible(float minX, float minY, float minZ,
                             float maxX, float maxY, float maxZ) {
        for (Plane p : planes) {
            float x = p.a >= 0 ? maxX : minX;
            float y = p.b >= 0 ? maxY : minY;
            float z = p.c >= 0 ? maxZ : minZ;
            if (p.a * x + p.b * y + p.c * z + p.d < 0.0f) return false;
        }
        return true;
    }

    private record Plane(float a, float b, float c, float d) {}
}
