package dev.f8q;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;

/** Sets the pack title when GLFW creates or renames a window, including early NeoForge. */
public final class WindowTitleAgent {
    public static void premain(String unused, Instrumentation instrumentation) {
        boolean available=false;
        try {
            var jar=java.nio.file.Path.of(WindowTitleAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            var helper=jar.resolveSibling("window-icon-helper.jar");
            if (java.nio.file.Files.isRegularFile(helper)) {
                instrumentation.appendToBootstrapClassLoaderSearch(new java.util.jar.JarFile(helper.toFile()));
                Class<?> icons=Class.forName("dev.f8q.IconSupport",true,null);
                try(var resources=new java.util.jar.JarFile(helper.toFile())) {
                    byte[] logo=resources.getInputStream(resources.getJarEntry("dev/f8q/window-icon.png")).readAllBytes();
                    icons.getMethod("configure",byte[].class).invoke(null,(Object)logo);
                }
                available=true;
            }
        } catch(Exception error) {
            System.err.println("[F8Q] Window icon helper unavailable; keeping the existing icon.");
        }
        final boolean iconsReady=available;
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String name, Class<?> type,
                    ProtectionDomain domain, byte[] original) {
                if (!"org/lwjgl/glfw/GLFW".equals(name)) return null;
                ClassReader reader = new ClassReader(original);
                ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
                reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
                    @Override public MethodVisitor visitMethod(int access, String method, String desc,
                            String signature, String[] exceptions) {
                        MethodVisitor target = super.visitMethod(access, method, desc, signature, exceptions);
                        boolean create = method.equals("glfwCreateWindow") && desc.equals("(IILjava/lang/CharSequence;JJ)J");
                        boolean rename = method.equals("glfwSetWindowTitle") && desc.equals("(JLjava/lang/CharSequence;)V");
                        if (iconsReady && method.equals("glfwSetWindowIcon") && desc.equals("(JLorg/lwjgl/glfw/GLFWImage$Buffer;)V")) {
                            return new MethodVisitor(Opcodes.ASM8, target) {
                                @Override public void visitCode() {
                                    super.visitCode();
                                    super.visitLdcInsn(Type.getObjectType("org/lwjgl/glfw/GLFW"));
                                    super.visitVarInsn(Opcodes.ALOAD, 2);
                                    super.visitMethodInsn(Opcodes.INVOKESTATIC,"dev/f8q/IconSupport","icon","(Ljava/lang/Class;Ljava/lang/Object;)Ljava/lang/Object;",false);
                                    super.visitTypeInsn(Opcodes.CHECKCAST,"org/lwjgl/glfw/GLFWImage$Buffer");
                                    super.visitVarInsn(Opcodes.ASTORE, 2);
                                }
                            };
                        }
                        if (!create && !rename) return target;
                        return new MethodVisitor(Opcodes.ASM8, target) {
                            @Override public void visitCode() {
                                super.visitCode();
                                super.visitLdcInsn("F8Q NEOFORGE 1.21.1");
                                super.visitVarInsn(Opcodes.ASTORE, 2);
                            }
                        };
                    }
                }, 0);
                return writer.toByteArray();
            }
        });
    }
}
