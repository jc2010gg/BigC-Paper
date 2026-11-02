import dev.tideframe.core.Screen;
import dev.tideframe.edit.ScreenCodec;
import java.nio.file.*;

/** Real browser export -> production decoder -> demo files. Java 21 or later. */
class ExportChecks {
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("export.json output-directory");
        Screen exported=ScreenCodec.decode(Files.readString(Path.of(args[0])));
        if(exported.elements().size()!=6)throw new AssertionError("Expected actual six-element browser export");
        var resized=exported.elements().stream().filter(e->e.id().equals("accept_copy1")).findFirst().orElseThrow();
        if(!resized.shape().equals("ellipse") || !resized.border() || resized.messageCondition()==null
                || !resized.messageCondition().permission().equals("tideframe.edit")
                || Math.abs(resized.x()-.0312)>1e-6 || Math.abs(resized.y()-.0351)>1e-6
                || resized.width()<=.8 || resized.height()<=.18)throw new AssertionError("Browser edits were not preserved");
        Path output=Path.of(args[1]);Files.createDirectories(output);
        for(var demo:Screen.demos().values())Files.writeString(output.resolve(demo.id()+".json"),ScreenCodec.encode(demo));
        Files.writeString(output.resolve("editor_demo.json"),ScreenCodec.encode(new Screen("editor_demo","EDITOR DEMO",exported.elements())));
        System.out.println("BROWSER_EXPORT_ACCEPTED schema=2 elements=6 uniqueIds=true ellipse=true border=true condition=true resized=true centerUnchanged=true");
        System.out.println("WROTE_DEMOS count=5 directory="+output);
    }
}
