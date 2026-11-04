import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** F8Q client policy: keep movement animation, disable Punchy camera and emotes. */
public class DisableMoves {
  static int changes;
  static void constant(MethodNode m, boolean bool) {
    m.instructions.clear(); m.tryCatchBlocks.clear();
    if(m.localVariables != null) m.localVariables.clear();
    m.visibleLocalVariableAnnotations=null; m.invisibleLocalVariableAnnotations=null;
    if(bool) m.instructions.add(new InsnNode(Opcodes.ICONST_0));
    m.instructions.add(new InsnNode(bool?Opcodes.IRETURN:Opcodes.RETURN));
    m.maxStack=bool?1:0; changes++;
  }
  static byte[] patch(String name, byte[] bytes) {
    ClassNode c=new ClassNode();new ClassReader(bytes).accept(c,0);
    for(MethodNode m:c.methods) {
      if(name.endsWith("EmoteKeybindManager.class")) {
        // Preserve the first-person menu, but skip the camera and emote branches.
        if(m.name.equals("handleKey")) for(AbstractInsnNode n:m.instructions.toArray()) {
          if(n instanceof FieldInsnNode f && f.getOpcode()==Opcodes.GETSTATIC &&
              Set.of("cameraToggle","cameraEditor","freeCamera","menu").contains(f.name)) {
            m.instructions.set(n,new InsnNode(Opcodes.ACONST_NULL));changes++;
          }
        }
        if(m.name.equals("isMenuKey")) constant(m,true);
        if(m.name.equals("trigger")) constant(m,false);
      }
      if(name.endsWith("ShoulderCameraSettings.class") && m.name.equals("enabled")) constant(m,true);
      if(name.endsWith("ShoulderCameraController.class")) {
        if(Set.of("toggle","toggleFreeCamera","openEditor","setEnabledFromEditor").contains(m.name)) constant(m,false);
        if(Set.of("isActive","isFreeCameraActive").contains(m.name)) constant(m,true);
      }
    }
    ClassWriter w=new ClassWriter(0);c.accept(w);return w.toByteArray();
  }
  public static void main(String[] args)throws Exception {
    try(JarFile in=new JarFile(args[0]);JarOutputStream out=new JarOutputStream(Files.newOutputStream(Path.of(args[1])))) {
      var all=in.entries();while(all.hasMoreElements()) {
        var e=all.nextElement();String n=e.getName();
        if(n.startsWith("META-INF/") && (n.endsWith(".SF")||n.endsWith(".RSA")||n.endsWith(".DSA"))) continue;
        byte[] b=in.getInputStream(e).readAllBytes();
        if(n.equals("punchymoves/client/emote/EmoteKeybindManager.class") ||
           n.equals("punchymoves/client/camera/ShoulderCameraSettings.class") ||
           n.equals("punchymoves/client/camera/ShoulderCameraController.class")) b=patch(n,b);
        out.putNextEntry(new JarEntry(n));out.write(b);out.closeEntry();
      }
      out.putNextEntry(new JarEntry("META-INF/f8q-camera-emotes-disabled.txt"));
      out.write("F8Q: Punchy Moves custom camera and emote actions disabled; movement animation preserved.\n".getBytes());out.closeEntry();
    }
    if(changes!=17) throw new IllegalStateException("Unexpected patch count: "+changes);
    System.out.println("Patched "+changes+" camera/emote entry points.");
  }
}
