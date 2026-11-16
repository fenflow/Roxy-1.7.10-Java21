package net.rasanovum.roxy.legacy.loader;

import static org.junit.Assert.*;

import java.io.InputStream;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class RenderBackendGuardTest {

    private static final String MANAGER = "com.gtnewhorizons.angelica.glsm.backend.BackendManager";

    public static class Backend {

        private final boolean sdl;

        public Backend(boolean sdl) {
            this.sdl = sdl;
        }

        public boolean isSDLGPU() {
            return sdl;
        }
    }

    public static class BrokenBackend {

        public boolean isSDLGPU() {
            throw new IllegalStateException("broken host");
        }
    }

    private static class HostLoader extends ClassLoader {

        HostLoader(Object backend) throws Exception {
            super(RenderBackendGuardTest.class.getClassLoader());
            ClassWriter writer = new ClassWriter(0);
            writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, MANAGER.replace('.', '/'), null, "java/lang/Object", null);
            writer
                .visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "RENDER_BACKEND", "Ljava/lang/Object;", null, null)
                .visitEnd();
            writer.visitEnd();
            byte[] bytes = writer.toByteArray();
            defineClass(MANAGER, bytes, 0, bytes.length).getField("RENDER_BACKEND")
                .set(null, backend);
        }
    }

    @Test
    public void sdlWorldStartupIsRejectedWithoutAnOpenGlContext() throws Exception {
        try {
            RenderBackendGuard.requireOpenGl(new HostLoader(new Backend(true)));
            fail("SDL-GPU must never reach Voxy's native OpenGL startup");
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("requires OpenGL"));
            assertTrue(
                expected.getMessage()
                    .contains("restart Minecraft"));
        }
    }

    @Test
    public void openGlBackendIsAllowed() throws Exception {
        RenderBackendGuard.requireOpenGl(new HostLoader(new Backend(false)));
    }

    @Test
    public void legacyAngelicaWithoutBackendManagerIsAllowed() {
        RenderBackendGuard.requireOpenGl(new ClassLoader(null) {});
    }

    @Test
    public void unknownBackendApiFailsClosed() throws Exception {
        assertRejected(new Object());
    }

    @Test
    public void uninitializedBackendFailsClosed() throws Exception {
        assertRejected(null);
    }

    @Test
    public void brokenBackendFailsClosed() throws Exception {
        assertRejected(new BrokenBackend());
    }

    @Test
    public void terrainAndDiagnosticsCheckBackendBeforeCallingOtherCode() throws Exception {
        assertGuardFirst("net/rasanovum/roxy/legacy/terrain/ModernTerrain", "start");
        assertGuardFirst("net/rasanovum/roxy/legacy/loader/ModernEngineProbe", "run");
        assertGuardFirst("net/rasanovum/roxy/legacy/loader/ModernEngineProbe", "models");
        assertGuardFirst("net/rasanovum/roxy/legacy/loader/ModernEngineProbe", "renderer");
    }

    private void assertGuardFirst(String name, String methodName) throws Exception {
        ClassNode type = new ClassNode();
        try (InputStream stream = getClass().getResourceAsStream("/" + name + ".class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(type, 0);
        }
        for (MethodNode method : type.methods) {
            if (!method.name.equals(methodName)) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                assertEquals("net/rasanovum/roxy/legacy/loader/RenderBackendGuard", call.owner);
                assertEquals("requireOpenGl", call.name);
                return;
            }
        }
        fail("Missing guarded entry point: " + name + "." + methodName);
    }

    private static void assertRejected(Object backend) throws Exception {
        try {
            RenderBackendGuard.requireOpenGl(new HostLoader(backend));
            fail("Cannot safely enter native OpenGL");
        } catch (IllegalStateException expected) {
            // A Java failure is recoverable by the terrain lifecycle; a native crash is not.
        }
    }
}
