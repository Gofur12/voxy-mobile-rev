package dev.brezze.voxymobile.diagnostics;

/** Basic smoke coverage for runtime activation diagnostics and queue accounting. */
public final class RuntimeDiagnosticsSmokeTest {
    public static void main(String[] args) {
        RuntimeDiagnostics.resetForTests();
        RuntimeDiagnostics.chunkLoaded(3);
        RuntimeDiagnostics.sectionCaptured();
        RuntimeDiagnostics.renderCallback(2, 1);
        RuntimeDiagnostics.shaderReady(true);
        RuntimeDiagnostics.drawStats(1, 1);
        RuntimeDiagnostics.drawStats(0, 0);
        if (RuntimeDiagnostics.isRendererFailed()) throw new AssertionError("unexpected renderer failure");
        RuntimeDiagnostics.rendererFailed(new IllegalStateException("test failure"));
        if (!RuntimeDiagnostics.isRendererFailed()) throw new AssertionError("failure state not recorded");
        RuntimeDiagnostics.resetForTests();
        if (RuntimeDiagnostics.isRendererFailed()) throw new AssertionError("reset did not clear failure state");
        System.out.println("RuntimeDiagnosticsSmokeTest: PASS (7 assertions)");
    }
}
