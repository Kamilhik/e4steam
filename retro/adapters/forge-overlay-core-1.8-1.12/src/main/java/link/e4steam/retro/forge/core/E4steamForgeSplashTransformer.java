package link.e4steam.retro.forge.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Disables Forge's second OpenGL drawable without editing splash.properties. */
public final class E4steamForgeSplashTransformer implements IClassTransformer {
    private static final String SPLASH_CLASS = "net.minecraftforge.fml.client.SplashProgress";
    private static final String SPLASH_OWNER = "net/minecraftforge/fml/client/SplashProgress";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !SPLASH_CLASS.equals(transformedName)
                || !shouldDisableSplash()) {
            return basicClass;
        }
        return disableSplashFlag(basicClass);
    }

    private static byte[] disableSplashFlag(byte[] basicClass) {
        try {
            ClassReader reader = new ClassReader(basicClass);
            ClassWriter writer = new ClassWriter(reader, 0);
            final boolean[] patched = {false};
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM4, writer) {
                @Override
                public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        String[] exceptions
                ) {
                    MethodVisitor delegate = super.visitMethod(
                            access, name, descriptor, signature, exceptions
                    );
                    if (!"start".equals(name) || !"()V".equals(descriptor)) {
                        return delegate;
                    }
                    return new MethodVisitor(Opcodes.ASM4, delegate) {
                        @Override
                        public void visitFieldInsn(
                                int opcode, String owner, String field, String fieldDescriptor
                        ) {
                            if (opcode == Opcodes.PUTSTATIC
                                    && SPLASH_OWNER.equals(owner)
                                    && "enabled".equals(field)
                                    && "Z".equals(fieldDescriptor)) {
                                super.visitInsn(Opcodes.POP);
                                super.visitInsn(Opcodes.ICONST_0);
                                patched[0] = true;
                            }
                            super.visitFieldInsn(opcode, owner, field, fieldDescriptor);
                        }
                    };
                }
            };
            reader.accept(visitor, 0);
            if (!patched[0]) {
                System.err.println("[e4steam] Forge SplashProgress layout was not recognized");
                return basicClass;
            }
            return writer.toByteArray();
        } catch (RuntimeException failure) {
            System.err.println("[e4steam] Could not patch Forge SplashProgress: " + failure);
            return basicClass;
        }
    }

    private static boolean shouldDisableSplash() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (!osName.contains("linux")) return false;
        return Boolean.parseBoolean(System.getProperty("e4steam.overlayRelaunch"));
    }
}
