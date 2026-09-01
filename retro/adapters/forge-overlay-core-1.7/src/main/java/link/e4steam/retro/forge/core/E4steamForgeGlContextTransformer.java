package link.e4steam.retro.forge.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Repairs the legacy LWJGL 2 display binding after macOS overlay injection. */
public final class E4steamForgeGlContextTransformer implements IClassTransformer {
    private static final String GL_ALLOCATION_CLASS =
            "net.minecraft.client.renderer.GLAllocation";
    private static final String MACOS_DISPLAY_CLASS = "org.lwjgl.opengl.MacOSXDisplay";
    private static final String GL11_OWNER = "org/lwjgl/opengl/GL11";
    private static final String COMPAT_OWNER =
            "link/e4steam/retro/forge/core/E4steamForgeGlContextTransformer";
    private static final int MAX_FALLBACK_LIST_COUNT = 4_096;
    private static int nextFallbackList = 1_000_000;
    private static volatile boolean recoveryReported;
    private static volatile boolean foregroundRequested;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !shouldRepairLegacyDisplayContext()) {
            return basicClass;
        }
        if (GL_ALLOCATION_CLASS.equals(transformedName)) {
            return wrapDisplayListAllocation(basicClass);
        }
        if (MACOS_DISPLAY_CLASS.equals(transformedName)) {
            return activateCreatedMacWindow(basicClass);
        }
        return basicClass;
    }

    private static byte[] activateCreatedMacWindow(byte[] basicClass) {
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
                    if (!"createWindow".equals(name)
                            || !"(Lorg/lwjgl/opengl/DrawableLWJGL;Lorg/lwjgl/opengl/DisplayMode;Ljava/awt/Canvas;II)V"
                            .equals(descriptor)) {
                        return delegate;
                    }
                    return new MethodVisitor(Opcodes.ASM4, delegate) {
                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode == Opcodes.RETURN) {
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        COMPAT_OWNER,
                                        "onMacDisplayCreated",
                                        "(Ljava/lang/Object;)V",
                                        false
                                );
                                patched[0] = true;
                            }
                            super.visitInsn(opcode);
                        }
                    };
                }
            };
            reader.accept(visitor, 0);
            if (!patched[0]) {
                System.err.println("[e4steam] macOS LWJGL 2 window layout was not recognized");
                return basicClass;
            }
            System.err.println("[e4steam] Installed the macOS LWJGL 2 window activation patch");
            return writer.toByteArray();
        } catch (RuntimeException failure) {
            System.err.println("[e4steam] Could not patch the macOS LWJGL 2 window: " + failure);
            return basicClass;
        }
    }

    /** Runs immediately after LWJGL has assigned its native NSWindow handle. */
    public static void onMacDisplayCreated(Object displayImplementation) {
        if (!shouldRepairLegacyDisplayContext()) return;
        if (MacOsCocoaWindowActivator.activate(displayImplementation)) {
            foregroundRequested = true;
            System.err.println("[e4steam] Scheduled the macOS Minecraft window activation");
        }
    }

    private static byte[] wrapDisplayListAllocation(byte[] basicClass) {
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
                    return new MethodVisitor(Opcodes.ASM4, delegate) {
                        @Override
                        public void visitMethodInsn(
                                int opcode,
                                String owner,
                                String method,
                                String methodDescriptor,
                                boolean isInterface
                        ) {
                            if (opcode == Opcodes.INVOKESTATIC
                                    && GL11_OWNER.equals(owner)
                                    && "glGenLists".equals(method)
                                    && "(I)I".equals(methodDescriptor)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        COMPAT_OWNER,
                                        "generateDisplayLists",
                                        "(I)I",
                                        false
                                );
                                patched[0] = true;
                                return;
                            }
                            super.visitMethodInsn(
                                    opcode, owner, method, methodDescriptor, isInterface
                            );
                        }
                    };
                }
            };
            reader.accept(visitor, 0);
            if (!patched[0]) {
                System.err.println("[e4steam] GLAllocation layout was not recognized");
                return basicClass;
            }
            System.err.println(
                    "[e4steam] Installed the macOS LWJGL 2 GLAllocation compatibility patch"
            );
            return writer.toByteArray();
        } catch (RuntimeException failure) {
            System.err.println("[e4steam] Could not patch GLAllocation: " + failure);
            return basicClass;
        }
    }

    /** Same contract as GL11.glGenLists, with one targeted context recovery attempt. */
    public static int generateDisplayLists(int count) {
        if (!shouldRepairLegacyDisplayContext()) {
            return GL11.glGenLists(count);
        }

        requestForegroundOnce();
        ensureDisplayCurrent(false);
        int generated = GL11.glGenLists(count);
        if (generated != 0 || count <= 0) return generated;

        for (int attempt = 0; attempt < 3 && generated == 0; attempt++) {
            processDisplayMessages();
            ensureDisplayCurrent(true);
            generated = GL11.glGenLists(count);
        }
        if (generated == 0 && GL11.glGetError() == GL11.GL_NO_ERROR) {
            generated = reserveDisplayLists(count);
        }
        if (generated != 0 && !recoveryReported) {
            recoveryReported = true;
            System.err.println("[e4steam] Recovered macOS LWJGL 2 display-list allocation");
        }
        return generated;
    }

    private static void requestForegroundOnce() {
        if (foregroundRequested || !Display.isCreated()) return;
        synchronized (E4steamForgeGlContextTransformer.class) {
            if (foregroundRequested) return;
            foregroundRequested = true;
            MacOsCocoaWindowActivator.activateCurrentDisplay();
            processDisplayMessages();
        }
    }

    private static void processDisplayMessages() {
        if (!Display.isCreated()) return;
        try {
            Display.processMessages();
        } catch (RuntimeException failure) {
            System.err.println(
                    "[e4steam] Could not process macOS LWJGL 2 window messages: " + failure
            );
        }
    }

    /**
     * Valve's macOS OpenGL interposer can return zero from glGenLists without
     * setting an OpenGL error. Empty lists reserve an equivalent contiguous
     * range, and Minecraft replaces each list normally when compiling models.
     */
    private static synchronized int reserveDisplayLists(int count) {
        if (count <= 0 || count > MAX_FALLBACK_LIST_COUNT) return 0;
        int first = nextFallbackList;
        if (first <= 0 || first > Integer.MAX_VALUE - count) return 0;

        int reserved = 0;
        try {
            for (int offset = 0; offset < count; offset++) {
                int list = first + offset;
                if (GL11.glIsList(list)) {
                    deleteReservedLists(first, reserved);
                    return 0;
                }
                GL11.glNewList(list, GL11.GL_COMPILE);
                GL11.glEndList();
                if (GL11.glGetError() != GL11.GL_NO_ERROR || !GL11.glIsList(list)) {
                    deleteReservedLists(first, reserved);
                    return 0;
                }
                reserved++;
            }
            nextFallbackList = first + count;
            return first;
        } catch (RuntimeException failure) {
            deleteReservedLists(first, reserved);
            System.err.println(
                    "[e4steam] Could not reserve fallback OpenGL display lists: " + failure
            );
            return 0;
        }
    }

    private static void deleteReservedLists(int first, int count) {
        if (count <= 0) return;
        try {
            GL11.glDeleteLists(first, count);
        } catch (RuntimeException ignored) {
            // Preserve the original allocation failure.
        }
    }

    private static void ensureDisplayCurrent(boolean forceRebind) {
        if (!Display.isCreated()) return;
        try {
            boolean current = Display.isCurrent();
            if (forceRebind && current) {
                Display.releaseContext();
                current = false;
            }
            if (!current) Display.makeCurrent();
        } catch (LWJGLException failure) {
            System.err.println(
                    "[e4steam] Could not rebind the macOS LWJGL 2 context: " + failure
            );
        }
    }

    private static boolean shouldRepairLegacyDisplayContext() {
        return false;
    }
}
