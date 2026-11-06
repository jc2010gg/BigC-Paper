import dev.linqfy.bigCasares.modules.tideframe.engine.core.*;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.*;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;

/** Reproducible offline raster/pack evidence; never a Minecraft screenshot or FPS test. */
class BuildHudDemo {
    public static void main(String[] args) throws Exception {
        if(args.length!=2) throw new IllegalArgumentException("screens-directory output-directory");
        var screens=new TreeMap<String,Screen>();
        try(var paths=Files.list(Path.of(args[0]))) {
            for(var path:paths.filter(p->p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                var screen=ScreenCodec.decode(Files.readString(path));
                if(screens.putIfAbsent(screen.id(),screen)!=null)throw new IllegalArgumentException("Duplicate screen");
            }
        }
        var output=Path.of(args[1]); Files.createDirectories(output);
        long start=System.nanoTime();
        var hud=HudAssets.create(screens.values());
        double bakeSeconds=(System.nanoTime()-start)/1_000_000_000.0;
        var images=ImageGlyphs.create(screens.values().stream().flatMap(s->s.elements().stream())
            .map(Screen.Element::image).filter(Objects::nonNull).toList());
        var artifact=PackGenerator.generate(output.resolve("pack"),images,hud);
        var menu=screens.get("menu");
        ImageIO.write(HudRaster.render(menu,null,8),"PNG",output.resolve("menu-raster.png").toFile());
        ImageIO.write(HudRaster.render(menu,"profile",8),"PNG",output.resolve("menu-hover-raster.png").toFile());
        if(screens.containsKey("image_demo")) ImageIO.write(HudRaster.render(screens.get("image_demo"),null,8),"PNG",output.resolve("image-raster.png").toFile());
        int checksum=0;long timing=System.nanoTime();
        for(int i=0;i<100_000;i++) {
            var frame=hud.frame(menu,(i&1)==0?null:"profile",8)
                +hud.cursor((i%255)/127.0-1,(i%143)/71.0-1);
            checksum+=frame.hashCode();
        }
        double composeNs=(System.nanoTime()-timing)/100_000.0;
        String json=String.format(Locale.ROOT,"""
            {"screens":%d,"canvasWidth":256,"canvasHeight":144,
             "bakeSeconds":%.6f,"composeCalls":100000,"meanComposeNanoseconds":%.3f,
             "checksum":%d,"packBytes":%d,"packSha256":"%s",
             "java":"%s","benchmarkScope":"local worker raster and glyph selection only",
             "minecraftVisualAcceptance":false,"clientFPSMeasured":false,"mouseLatencyMeasured":false}
            """,screens.size(),bakeSeconds,composeNs,checksum,Files.size(artifact.path()),
            HexFormat.of().formatHex(artifact.sha256()),Runtime.version());
        Files.writeString(output.resolve("measurements.json"),json);
        System.out.println(json);
    }
}
