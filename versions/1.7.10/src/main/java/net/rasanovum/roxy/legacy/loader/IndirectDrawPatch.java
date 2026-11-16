package net.rasanovum.roxy.legacy.loader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Routes the pinned renderer's two count-buffer submissions through a driver compatibility adapter. */
final class IndirectDrawPatch {

    static byte[] apply(String name, byte[] input) {
        if (!name.equals("me.cortex.voxy.client.core.rendering.section.backend.mdic.MDICSectionRenderer")) return input;
        ClassNode type = new ClassNode(Opcodes.ASM9);
        new ClassReader(input).accept(type, 0);
        for (MethodNode method : type.methods) for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (!(insn instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) insn;
            if (call.getOpcode() == Opcodes.INVOKESTATIC && call.owner.equals("org/lwjgl/opengl/ARBIndirectParameters")
                && call.name.equals("glMultiDrawElementsIndirectCountARB")
                && call.desc.equals("(IIJJII)V")) {
                call.owner = "net/rasanovum/roxy/legacy/modern/IndirectDrawCompatibility";
                call.name = "drawIndirectCount";
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        type.accept(writer);
        return writer.toByteArray();
    }

}
