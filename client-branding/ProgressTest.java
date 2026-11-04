import java.nio.file.*;
import com.customloadingscreen.scene.Scene;
public class ProgressTest {
  public static void main(String[] a)throws Exception {
    Scene scene=Scene.load(Files.newInputStream(Path.of(a[0])));
    var fill=scene.elements().stream().filter(e->e.id.equals("progress_fill")).findFirst().orElseThrow();
    scene.onStageChanged(Scene.Stage.RESOURCE_LOADING);
    double time=0;
    for(double p:new double[]{0,0.25,0.5,0.75,1}) {
      scene.update(time+=1,p,p);
      if(Math.abs(fill.currentWidth-p*640)>0.01) throw new AssertionError("Progress "+p+" gave width "+fill.currentWidth);
      System.out.println("Loader progress "+p+" -> fill width "+fill.currentWidth);
    }
    var label=scene.elements().stream().filter(e->e.id.equals("stage_resource_loading")).findFirst().orElseThrow();
    if(label.x!=640 || label.y>=728 || label.currentOpacity<0.99) throw new AssertionError("Stage text alignment or visibility");
    System.out.println("PASS: real scene engine tracks 0/25/50/75/100 percent; stage label is above the left edge.");
  }
}
