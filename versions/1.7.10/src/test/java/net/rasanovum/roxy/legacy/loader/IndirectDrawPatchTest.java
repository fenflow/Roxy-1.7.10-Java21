package net.rasanovum.roxy.legacy.loader;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.util.zip.ZipFile;

import org.junit.Assume;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

public class IndirectDrawPatchTest {

    @Test
    public void pinnedOpaqueAndTranslucentSubmissionsRemainVerifiable() throws Exception {
        String jar = System.getProperty("roxy.voxyJar");
        Assume.assumeNotNull(jar);
        String name = "me.cortex.voxy.client.core.rendering.section.backend.mdic.MDICSectionRenderer";
        byte[] original;
        try (ZipFile zip = new ZipFile(jar);
            InputStream input = zip.getInputStream(zip.getEntry(name.replace('.', '/') + ".class"))) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int size;
            while ((size = input.read(buffer)) != -1) out.write(buffer, 0, size);
            original = out.toByteArray();
        }
        assertSame(original, IndirectDrawPatch.apply("unrelated.Renderer", original));
        ClassNode patched = new ClassNode();
        new ClassReader(IndirectDrawPatch.apply(name, original)).accept(patched, 0);
        int routed = 0;
        for (MethodNode method : patched.methods) {
            new Analyzer<>(new BasicVerifier()).analyze(patched.name, method);
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                assertNotEquals("glMultiDrawElementsIndirectCountARB", call.name);
                if (!call.owner.equals("net/rasanovum/roxy/legacy/modern/IndirectDrawCompatibility")) continue;
                assertEquals("drawIndirectCount", call.name);
                assertEquals("(IIJJII)V", call.desc);
                assertTrue(method.name.equals("renderTerrain") || method.name.equals("renderTranslucent"));
                routed++;
            }
        }
        assertEquals(2, routed);
    }
}
