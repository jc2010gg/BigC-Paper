import dev.linqfy.bigCasares.modules.tideframe.engine.core.*;import dev.linqfy.bigCasares.modules.tideframe.engine.pack.*;import java.util.*;import java.security.*;import java.nio.charset.StandardCharsets;
public class PresentationFingerprint {public static void main(String[] args)throws Exception {
var e=new Screen.Element("button","OWN DEMO",0,0,1,.4,"button","close","rounded",true,null,true,null);
for(String mode:List.of("legacy","low")) {
var screen=new Screen("sample","SAMPLE",List.of(e),mode.equals("legacy")?RenderQuality.legacy():RenderQuality.low());
var entries=HudAssets.create(List.of(screen)).entries();var digest=MessageDigest.getInstance("SHA-256");
for(var entry:new TreeMap<>(entries).entrySet()){digest.update(entry.getKey().getBytes(StandardCharsets.UTF_8));digest.update((byte)0);digest.update(entry.getValue());}
System.out.println(mode+"\t"+entries.size()+"\t"+HexFormat.of().formatHex(digest.digest()));
}}}