package net.rasanovum.roxy.legacy.bridge;

import java.util.List;

import net.minecraft.client.renderer.Tessellator;

/** Balances GTNHLib's translation stack even when a legacy renderer throws mid-quad. */
final class ModelCaptureSession implements AutoCloseable {

    private final Class<?> manager;
    final Tessellator tessellator;

    ModelCaptureSession(Class<?> manager) throws Exception {
        this.manager = manager;
        tessellator = (Tessellator) manager.getMethod("startCapturingAndGet")
            .invoke(null);
    }

    List<?> finish() throws Exception {
        return (List<?>) manager.getMethod("stopCapturingToPooledQuads")
            .invoke(null);
    }

    @Override
    public void close() throws Exception {
        try {
            if ((Boolean) manager.getMethod("isCurrentlyCapturing")
                .invoke(null)) {
                // cleanup() alone clears capture mode but does not pop the saved translation.
                // Discard incomplete primitives before stopping; do not draw a failed model.
                manager.getMethod("discardTessellator", Tessellator.class)
                    .invoke(null, tessellator);
                manager.getMethod("stopCapturingToPooledQuads")
                    .invoke(null);
            }
        } finally {
            manager.getMethod("cleanup")
                .invoke(null);
        }
    }
}
