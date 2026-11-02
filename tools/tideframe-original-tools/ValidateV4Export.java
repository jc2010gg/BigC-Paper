import dev.tideframe.edit.ScreenCodec;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Validate the actual browser downloads and canonical round trip with the production decoder. */
class ValidateV4Export {
    public static void main(String[] args) throws Exception {
        if(args.length==0)throw new IllegalArgumentException("JSON file paths required");
        for(String input:args) {
            Path path=Path.of(input);byte[] bytes=Files.readAllBytes(path);
            var screen=ScreenCodec.decode(Files.readString(path));screen.validateActionAreas();
            if(!ScreenCodec.decode(ScreenCodec.encode(screen)).equals(screen))throw new AssertionError("Round trip differs");
            System.out.printf("V4_EXPORT_PASSED file=%s quality=%s width=%d height=%d elements=%d buttons=%d bytes=%d sha256=%s MinecraftClient=false%n",
                path.getFileName(),screen.quality().preset(),screen.quality().width(),screen.quality().height(),screen.elements().size(),screen.actionTargets().size(),bytes.length,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        }
    }
}
