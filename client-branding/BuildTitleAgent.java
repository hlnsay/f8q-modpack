import java.nio.file.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
public class BuildTitleAgent {
  static byte[] shade(byte[] bytes) {
    ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(0);
    reader.accept(new ClassRemapper(writer,new Remapper(Opcodes.ASM9) {
      @Override public String map(String name) {
        return name.startsWith("org/objectweb/asm/")?"dev/f8q/shaded/asm/"+name.substring(18):name;
      }
    }),0);return writer.toByteArray();
  }
  public static void main(String[] a)throws Exception {
    try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(Path.of(a[2])))) {
      out.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));out.write("Manifest-Version: 1.0\nPremain-Class: dev.f8q.WindowTitleAgent\n\n".getBytes());out.closeEntry();
      try(JarFile asm=new JarFile(a[0])) {var all=asm.entries();while(all.hasMoreElements()) {var e=all.nextElement();if(!e.getName().endsWith(".class")||e.getName().equals("module-info.class"))continue;
        out.putNextEntry(new JarEntry(e.getName().replace("org/objectweb/asm/","dev/f8q/shaded/asm/")));out.write(shade(asm.getInputStream(e).readAllBytes()));out.closeEntry();
      }}
      Path root=Path.of(a[1]);try(var files=Files.walk(root)) {for(Path p:files.filter(x->x.toString().endsWith(".class")).toList()) {
        out.putNextEntry(new JarEntry(root.relativize(p).toString().replace('\\','/')));out.write(shade(Files.readAllBytes(p)));out.closeEntry();
      }}
      out.putNextEntry(new JarEntry("ASM-NOTICE.txt"));out.write("Includes ASM 9.10.1, relocated to dev.f8q.shaded.asm. ASM is distributed under the BSD 3-Clause license. https://asm.ow2.io/license.html\n".getBytes());out.closeEntry();
    }
  }
}
