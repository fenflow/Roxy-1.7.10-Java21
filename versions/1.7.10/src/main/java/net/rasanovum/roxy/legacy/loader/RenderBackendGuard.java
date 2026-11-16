package net.rasanovum.roxy.legacy.loader;

/** Check the host backend without issuing native OpenGL calls. */
public final class RenderBackendGuard {

    private RenderBackendGuard() {}

    public static void requireOpenGl() {
        requireOpenGl(RenderBackendGuard.class.getClassLoader());
    }

    static void requireOpenGl(ClassLoader loader) {
        Class<?> manager;
        try {
            manager = Class.forName("com.gtnewhorizons.angelica.glsm.backend.BackendManager", true, loader);
        } catch (ClassNotFoundException legacyAngelica) {
            // Older Angelica builds without selectable backends use OpenGL.
            return;
        }
        try {
            requireOpenGlBackend(
                manager.getField("RENDER_BACKEND")
                    .get(null));
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(
                "Cannot verify Angelica's render backend; Roxy rendering is disabled.",
                error);
        }
    }

    static void requireOpenGlBackend(Object backend) throws ReflectiveOperationException {
        if (backend == null) throw new IllegalStateException("Angelica's render backend is not initialized.");
        if ((Boolean) backend.getClass()
            .getMethod("isSDLGPU")
            .invoke(backend)) {
            throw new IllegalStateException(
                "Roxy/Voxy requires OpenGL; Angelica SDL-GPU (Vulkan/Direct3D/Metal) is unsupported. "
                    + "Select Angelica's OpenGL backend and restart Minecraft to use Roxy.");
        }
    }
}
