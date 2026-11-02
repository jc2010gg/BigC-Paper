import dev.tideframe.core.ImageAsset;
import dev.tideframe.pack.BossBarAssets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.ZipFile;

/** Confirms the actual operator portrait pixels are in every served boss frame. */
public final class VerifyBossFacePack {
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Expected portrait PNG and actual pack ZIP");
        var portrait=ImageAsset.fromPng(Files.readAllBytes(Path.of(args[0])));
        var expected=BossBarAssets.create(portrait).entries();
        try(var zip=new ZipFile(args[1])) {
            for(var entry:expected.entrySet()) {
                var actual=zip.getEntry(entry.getKey());
                if(actual==null||!Arrays.equals(entry.getValue(),zip.getInputStream(actual).readAllBytes()))
                    throw new AssertionError("Actual boss pack entry differs: "+entry.getKey());
            }
            String provenance=new String(zip.getInputStream(zip.getEntry("ASSET_PROVENANCE.txt")).readAllBytes(),StandardCharsets.UTF_8);
            if(!provenance.contains(portrait.sha256()))throw new AssertionError("Portrait source hash missing");
        }
        System.out.println("{\"portraitSha256\":\""+portrait.sha256()+"\",\"width\":"+portrait.width()+",\"height\":"+portrait.height()
            +",\"actualBossEntriesMatched\":"+expected.size()+",\"nativeClientVerified\":false}");
    }
}
