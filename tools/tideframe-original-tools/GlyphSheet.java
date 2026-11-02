import dev.tideframe.core.LiveText;
import dev.tideframe.pack.Glyphs;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes every glyph of the project 5x7 font (ASCII, real lower case, accented letters, symbols) as JSON
 * {"hex code point": "rows"} for tools/make-web-font.py. Run with the compiled plugin classes on the class path:
 * java -cp build/classes/java/main tools/GlyphSheet.java build/glyphs.json
 */
public class GlyphSheet {
    public static void main(String[] args) throws Exception {
        var out = new StringBuilder("{\n");
        String all = " " + LiveText.CHARSET + LiveText.EXTRA;
        for (int i = 0; i < all.length(); i++) {
            char c = all.charAt(i);
            String rows = c == ' ' ? "00000/00000/00000/00000/00000/00000/00000" : Glyphs.pattern(c);
            out.append(String.format(java.util.Locale.ROOT, "  \"%04x\": \"%s\"%s%n", (int) c, rows, i + 1 < all.length() ? "," : ""));
        }
        Path target = Path.of(args.length > 0 ? args[0] : "build/glyphs.json");
        if (target.getParent() != null) Files.createDirectories(target.getParent());
        Files.writeString(target, out.append("}\n"));
        System.out.println("Wrote " + all.length() + " glyphs to " + target);
    }
}
