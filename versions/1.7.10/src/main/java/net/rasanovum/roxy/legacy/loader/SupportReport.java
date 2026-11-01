package net.rasanovum.roxy.legacy.loader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

import org.lwjgl.opengl.GL11;

/** Allowlisted support data. Never copies log messages, paths or world identity. */
public final class SupportReport {

    private static final Deque<String> FAILURES = new ArrayDeque<String>();

    private SupportReport() {}

    public static synchronized void failure(Throwable error) {
        StringBuilder result = new StringBuilder();
        for (int depth = 0; error != null && depth < 8; depth++) {
            result.append(
                error.getClass()
                    .getName())
                .append('\n');
            StackTraceElement[] frames = error.getStackTrace();
            for (int i = 0; i < Math.min(24, frames.length); i++) {
                StackTraceElement frame = frames[i];
                // Omit exception messages, source filenames and classloader/module descriptions.
                result.append("  ")
                    .append(frame.getClassName())
                    .append('.')
                    .append(frame.getMethodName())
                    .append(':')
                    .append(frame.getLineNumber())
                    .append('\n');
            }
            if (error.getCause() == error) break;
            error = error.getCause();
        }
        if (FAILURES.size() == 16) FAILURES.removeFirst();
        FAILURES.addLast(result.toString());
    }

    public static synchronized Path write(Path game, boolean enabled, int distance) throws Exception {
        StringBuilder out = new StringBuilder(
            "Roxy support report v2\nException messages and personal game identifiers are omitted. Review before sharing.\n");
        out.append("minecraft.version=1.7.10\n");
        out.append(net.rasanovum.roxy.legacy.terrain.ModernTerrain.INSTANCE.supportState());
        for (String key : new String[] { "java.version", "java.vm.name", "os.name", "os.version", "os.arch" })
            out.append(key)
                .append('=')
                .append(System.getProperty(key))
                .append('\n');
        out.append("gpu.vendor=")
            .append(GL11.glGetString(GL11.GL_VENDOR))
            .append('\n');
        out.append("gpu.renderer=")
            .append(GL11.glGetString(GL11.GL_RENDERER))
            .append('\n');
        out.append("opengl.version=")
            .append(GL11.glGetString(GL11.GL_VERSION))
            .append('\n');
        out.append("cpu.logicalProcessors=")
            .append(
                Runtime.getRuntime()
                    .availableProcessors())
            .append('\n');
        out.append("java.maxHeapMiB=")
            .append(
                Runtime.getRuntime()
                    .maxMemory() / 1048576)
            .append('\n');
        out.append("renderer.enabled=")
            .append(enabled)
            .append("\ndistance.chunks=")
            .append(distance)
            .append('\n');
        for (cpw.mods.fml.common.ModContainer mod : cpw.mods.fml.common.Loader.instance()
            .getModList()) {
            if (java.util.Arrays.asList("roxy", "angelica", "gtnhlib", "lwjgl3ify", "endlessids", "Forge", "FML")
                .contains(mod.getModId()))
                out.append("mod.")
                    .append(mod.getModId())
                    .append('=')
                    .append(mod.getVersion())
                    .append('\n');
        }
        out.append("Recent failure types and code locations (oldest first):\n");
        if (FAILURES.isEmpty()) out.append("No failures recorded in this session.\n");
        for (String failure : FAILURES) out.append(failure)
            .append('\n');
        Path destination = game.resolve("roxy/support-report.txt");
        Files.createDirectories(destination.getParent());
        Files.write(
            destination,
            out.toString()
                .getBytes(StandardCharsets.UTF_8));
        return destination;
    }
}
