import dev.tideframe.core.*;
import dev.tideframe.edit.ScreenCodec;
import dev.tideframe.pack.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Real downloaded editor JSON -> production decoder -> actual pack bytes. */
class ValidateV3Export {
    public static void main(String[] args) throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("browser.json clipboard.json output-directory");
        var browser=ScreenCodec.decode(Files.readString(Path.of(args[0])));
        var copies=ScreenCodec.decode(Files.readString(Path.of(args[1])));
        if(browser.elements().size()!=5 || copies.elements().size()!=7)throw new AssertionError("Unexpected browser element counts");
        var picture=browser.elements().stream().filter(e->e.id().equals("picture")).findFirst().orElseThrow();
        if(picture.background() || picture.image()==null || picture.image().width()!=48 || picture.image().height()!=32)
            throw new AssertionError("PNG or transparent background was lost");
        if(browser.elements().stream().anyMatch(Screen.Element::background))throw new AssertionError("Defaults must remain transparent");
        var images=copies.elements().stream().map(Screen.Element::image).filter(Objects::nonNull).toList();
        if(images.size()!=2 || !images.getFirst().equals(images.getLast()))throw new AssertionError("PNG copy changed its content");
        if(copies.elements().stream().noneMatch(e->e.image()!=null && e.kind().equals("button") && e.action().equals("message:PNG selected.")))
            throw new AssertionError("Image button configuration missing");
        var registry=ImageGlyphs.create(images);if(registry.size()!=1)throw new AssertionError("Duplicate images did not deduplicate");
        var artifact=PackGenerator.generate(Path.of(args[2]).resolve("browser-pack"),registry);
        try(var pack=new ZipFile(artifact.path().toFile())){
            byte[] embedded=pack.getInputStream(pack.getEntry("assets/tideframe/textures/font/images/"+picture.image().sha256()+".png")).readAllBytes();
            if(!Arrays.equals(embedded,picture.image().png()))throw new AssertionError("Pack changed canonical RGBA PNG bytes");
        }
        var screen=new Screen("editor_v3_demo","EDITOR PNG DEMO",browser.elements());
        Files.writeString(Path.of(args[2]).resolve("editor_v3_demo.json"),ScreenCodec.encode(screen));
        System.out.println("V3_BROWSER_ROUNDTRIP schema=3 elements=5 clipboardElements=7 backgroundTransparent=true png=48x32 labelsAndButtons=true copiedPngIdentical=true uniqueImages=1 packPngBytesIdentical=true");
    }
}
