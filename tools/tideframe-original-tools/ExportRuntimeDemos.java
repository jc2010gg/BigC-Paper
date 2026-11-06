import dev.linqfy.bigCasares.modules.tideframe.engine.core.CloseControl;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.RuntimeDemoCatalog;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.HudRaster;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/** Export our original pre-baked runtime demos for the editor; no external assets. */
public class ExportRuntimeDemos {
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Usage: ExportRuntimeDemos <repository>");
        Path root=Path.of(args[0]).toAbsolutePath().normalize();
        if(!Files.isRegularFile(root.resolve("build.gradle.kts")))throw new IllegalArgumentException("Expected Tideframe repository root");
        Path target=root.resolve("examples/runtime-demo"),evidence=root.resolve("evidence");Files.createDirectories(target);Files.createDirectories(evidence);
        var rows=new ArrayList<String>();var selected=List.of("image_demo08","sequence_demo","roulette_demo","overlay_demo");
        for(String id:selected)if(!RuntimeDemoCatalog.screens().containsKey(id))throw new IllegalStateException("Unknown preview screen: "+id);
        var preview=new BufferedImage(1024,576,BufferedImage.TYPE_INT_ARGB);var graphics=preview.createGraphics();
        try {
            for(var original:RuntimeDemoCatalog.screens().values()) {
                boolean overlay=original.id().equals("overlay_demo");
                var presentation=new Screen.Presentation(overlay?"overlay":"menu",overlay?1:2,overlay?"text":"block");
                var screen=CloseControl.prepare(new Screen(original.id(),original.title(),original.elements(),original.quality(),presentation));
                String json=ScreenCodec.encode(screen);if(!ScreenCodec.decode(json).equals(screen))throw new IllegalStateException("Runtime demo roundtrip failed: "+screen.id());
                byte[] bytes=(json+"\n").getBytes(StandardCharsets.UTF_8);Files.write(target.resolve(screen.id()+".json"),bytes);
                String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                rows.add("{\"id\":\""+screen.id()+"\",\"bytes\":"+bytes.length+",\"sha256\":\""+hash+"\"}");
                int index=selected.indexOf(screen.id());if(index>=0) {
                    var raster=HudRaster.render(screen,null,8);if(!ImageIO.write(raster,"png",evidence.resolve("runtime-v08-"+screen.id()+".png").toFile()))throw new IllegalStateException("PNG encoder unavailable");
                    graphics.drawImage(raster,index%2*512,index/2*288,null);
                }
            }
        } finally { graphics.dispose(); }
        if(rows.size()!=48)throw new IllegalStateException("Expected48original runtime frames");
        ImageIO.write(preview,"png",evidence.resolve("runtime-v08-previews.png").toFile());
        String report="{\"screens\":48,\"menus\":47,\"overlays\":1,\"menuScale\":2,\"menuCursor\":\"block\",\"overlayScale\":1,\"overlayCursor\":\"text\",\"schema\":6,\"roundtripVerified\":true,\"clientVisualVerification\":false,\"files\":["+String.join(",",rows)+"]}\n";
        Files.writeString(evidence.resolve("runtime-v08-export.json"),report,StandardCharsets.UTF_8);
        System.out.println("Exported48original frames, schema6 roundtrips verified. Menus scale2/block; passiveoverlay scale1/text. Previews are raster proofs, not Minecraft client acceptance.");
    }
}
