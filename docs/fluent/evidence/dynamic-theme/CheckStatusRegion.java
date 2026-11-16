import java.nio.file.*;
import javax.imageio.ImageIO;
public class CheckStatusRegion {
  public static void main(String[] args) throws Exception {
    for (String file : args) {
      var image = ImageIO.read(Path.of(file).toFile());
      // The captured API37 status window is [0,0][1080,63]. This region excludes
      // its clock (x<200) and connectivity/battery symbols (x>600), while crossing
      // the app heading/subtitle's glyph area if they actually drew over the bar.
      int expected = image.getRGB(400, 10) & 0xffffff, changed = 0;
      for (int y = 0; y < 63; y++) for (int x = 200; x < 500; x++)
        if ((image.getRGB(x,y) & 0xffffff) != expected) changed++;
      System.out.println(Path.of(file).getFileName()+": non-background pixels in status text exclusion region = "+changed);
    }
  }
}
