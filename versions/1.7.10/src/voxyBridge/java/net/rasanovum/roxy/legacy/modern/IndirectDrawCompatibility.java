package net.rasanovum.roxy.legacy.modern;

import static org.lwjgl.opengl.GL45C.*;

import org.lwjgl.opengl.ARBIndirectParameters;
import org.lwjgl.system.MemoryStack;

/** Preserves Voxy's generated commands on Mesa D3D12, where multi-draw stalls or loses terrain. */
public final class IndirectDrawCompatibility {

    private static Boolean directDraws;

    public static void drawIndirectCount(int mode, int type, long indirect, long countOffset, int maximum, int stride) {
        if (directDraws == null) {
            String renderer = glGetString(GL_RENDERER);
            String version = glGetString(GL_VERSION);
            directDraws = renderer != null && renderer.startsWith("D3D12 (")
                && version != null
                && version.contains("Mesa");
        }
        if (!directDraws) {
            ARBIndirectParameters
                .glMultiDrawElementsIndirectCountARB(mode, type, indirect, countOffset, maximum, stride);
            return;
        }
        // Shader-produced counts and commands need visibility to readback as well as command execution.
        // Reading only the count and using non-count multi-draw still loses terrain on this driver.
        glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.IntBuffer count = stack.mallocInt(1);
            glGetBufferSubData(ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB, countOffset, count);
            int draws = (int) Math.min(Integer.toUnsignedLong(count.get(0)), maximum);
            if (draws == 0) return;
            int step = stride == 0 ? 5 : stride / 4;
            int[] commands = new int[(draws - 1) * step + 5];
            glGetBufferSubData(GL_DRAW_INDIRECT_BUFFER, indirect, commands);
            int indexBytes = type == GL_UNSIGNED_INT ? 4 : type == GL_UNSIGNED_SHORT ? 2 : 1;
            for (int i = 0; i < draws; i++) {
                int offset = i * step;
                glDrawElementsInstancedBaseVertexBaseInstance(
                    mode,
                    commands[offset],
                    type,
                    Integer.toUnsignedLong(commands[offset + 2]) * indexBytes,
                    commands[offset + 1],
                    commands[offset + 3],
                    commands[offset + 4]);
            }
        }
    }
}
