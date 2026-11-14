package dev.brezze.voxymobile.render.streaming;

/**
 * CPU-only directional priority policy. It biases otherwise distance-ranked mesh work
 * toward the camera's view and current travel direction without replacing distance order.
 */
public final class PredictiveStreamingController {
    private static final double MAX_DIRECTIONAL_BIAS = 0.22;
    private static final double MOVEMENT_BLEND_MAX = 0.55;
    private static final double MOVEMENT_BLEND_SPEED = 8.0;

    /**
     * Produces a finite, non-negative priority score; lower scores are scheduled first.
     * The predicted distance remains the base score, while alignment adds a bounded bias.
     */
    public double priorityDistanceSquared(
            double targetX, double targetY, double targetZ,
            double cameraX, double cameraY, double cameraZ,
            double viewX, double viewY, double viewZ,
            double velocityX, double velocityY, double velocityZ,
            double predictedDistanceSquared) {
        if (!Double.isFinite(predictedDistanceSquared) || predictedDistanceSquared < 0.0)
            return Double.MAX_VALUE;

        double tx = targetX - cameraX;
        double ty = targetY - cameraY;
        double tz = targetZ - cameraZ;
        double targetLength = length(tx, ty, tz);
        if (targetLength < 1.0e-6) return predictedDistanceSquared;

        double viewLength = length(viewX, viewY, viewZ);
        if (!Double.isFinite(viewLength) || viewLength < 1.0e-6) return predictedDistanceSquared;
        double vx = viewX / viewLength;
        double vy = viewY / viewLength;
        double vz = viewZ / viewLength;

        double speed = length(velocityX, velocityY, velocityZ);
        double movementBlend = Double.isFinite(speed)
                ? Math.min(MOVEMENT_BLEND_MAX, speed / MOVEMENT_BLEND_SPEED * MOVEMENT_BLEND_MAX) : 0.0;
        if (speed > 1.0e-6 && Double.isFinite(speed)) {
            double inverseSpeed = 1.0 / speed;
            vx = vx * (1.0 - movementBlend) + velocityX * inverseSpeed * movementBlend;
            vy = vy * (1.0 - movementBlend) + velocityY * inverseSpeed * movementBlend;
            vz = vz * (1.0 - movementBlend) + velocityZ * inverseSpeed * movementBlend;
            double blendedLength = length(vx, vy, vz);
            if (blendedLength > 1.0e-6) {
                vx /= blendedLength; vy /= blendedLength; vz /= blendedLength;
            }
        }

        double alignment = (tx * vx + ty * vy + tz * vz) / targetLength;
        alignment = Math.max(-1.0, Math.min(1.0, alignment));
        double multiplier = 1.0 - alignment * MAX_DIRECTIONAL_BIAS;
        double score = predictedDistanceSquared * multiplier;
        return Double.isFinite(score) && score >= 0.0 ? score : Double.MAX_VALUE;
    }

    private static double length(double x, double y, double z) {
        return Math.sqrt(x * x + y * y + z * z);
    }
}
