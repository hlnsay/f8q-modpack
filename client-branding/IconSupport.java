package dev.f8q;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.imageio.ImageIO;

/** Uses the real GLFW class loader so early and game modules share the right icon type. */
public final class IconSupport {
    private static Object icon;
    private static ByteBuffer pixels;
    private static byte[] logo;
    public static void configure(byte[] bytes) { logo=bytes; }
    public static synchronized Object icon(Class<?> glfw, Object original) {
        try {
            if (icon != null) return icon;
            BufferedImage source=ImageIO.read(new java.io.ByteArrayInputStream(logo));
            BufferedImage scaled=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
            var graphics=scaled.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source,0,0,64,64,null);graphics.dispose();
            pixels=ByteBuffer.allocateDirect(64*64*4).order(ByteOrder.nativeOrder());
            for(int y=0;y<64;y++)for(int x=0;x<64;x++) {
                int rgba=scaled.getRGB(x,y);pixels.put((byte)(rgba>>16)).put((byte)(rgba>>8)).put((byte)rgba).put((byte)(rgba>>24));
            }
            pixels.flip();
            Class<?> image=Class.forName("org.lwjgl.glfw.GLFWImage",true,glfw.getClassLoader());
            Object buffer=image.getMethod("malloc",int.class).invoke(null,1);
            buffer.getClass().getMethod("width",int.class).invoke(buffer,64);
            buffer.getClass().getMethod("height",int.class).invoke(buffer,64);
            buffer.getClass().getMethod("pixels",ByteBuffer.class).invoke(buffer,pixels);
            icon=buffer;return buffer;
        } catch (Throwable error) {
            System.err.println("[F8Q] Custom window icon unavailable: "+error.getClass().getSimpleName()+": "+error.getMessage());
            return original;
        }
    }
}
