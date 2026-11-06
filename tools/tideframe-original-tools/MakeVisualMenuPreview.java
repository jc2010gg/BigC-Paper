import dev.linqfy.bigCasares.modules.tideframe.engine.core.VisualMenuCatalog;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.HudRaster;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;

/** Exports an original raster preview and importable schema6 layout, never a client screenshot. */
public final class MakeVisualMenuPreview {
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Expected Tideframe repository root");
        Path root=Path.of(args[0]).toAbsolutePath().normalize();
        if(!Files.isRegularFile(root.resolve("build.gradle.kts")))throw new IllegalArgumentException("Expected repository root");
        var screen=VisualMenuCatalog.screen();
        Path evidence=root.resolve("evidence"), examples=root.resolve("examples/visual-menu-v09");
        Files.createDirectories(evidence);Files.createDirectories(examples);
        Files.writeString(examples.resolve(screen.id()+".json"),ScreenCodec.encode(screen)+"\n");
        for(String hover:new String[]{"none","sequence","roulette","hud"}) {
            var image=HudRaster.render(screen,hover.equals("none")?null:hover,8);
            if(!ImageIO.write(image,"png",evidence.resolve("visual-menu-v09-"+hover+".png").toFile()))
                throw new IllegalStateException("PNG encoder unavailable");
        }
        for(var element:screen.elements())if(element.image()!=null)
            Files.write(examples.resolve(element.id()+".png"),element.image().png());
        System.out.println("VISUAL_MENU_09 exported1080p variants=4 images=4 schema6 roundtrip="
                +screen.equals(ScreenCodec.decode(ScreenCodec.encode(screen)))+" clientScreenshot=false");
    }
}
