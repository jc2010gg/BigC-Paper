import dev.tideframe.edit.ScreenCodec;
import dev.tideframe.pack.*;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Reproducible cold-process bake observation; not a client, FPS or network-latency benchmark.
 * Run each profile separately with-Xmx512m and the project's compiled classes on classpath.
 */
class CreationBenchmark {
    public static void main(String[] args) throws Exception {
        if (args.length != 1)
            throw new IllegalArgumentException("Expected repository root");
        Path root = Path.of(args[0]).toAbsolutePath(); String profile = "low";
        Path source = root.resolve("examples/creation-demo/creation_demo.json");
        byte[] json = Files.readAllBytes(source);
        var heap = ManagementFactory.getMemoryMXBean();
        long before = heap.getHeapMemoryUsage().getUsed();
        var peak = new AtomicLong(before); var sampling = new AtomicBoolean(true);
        var sampler = new Thread(() -> {
            while (sampling.get()) {
                peak.accumulateAndGet(heap.getHeapMemoryUsage().getUsed(), Math::max);
                try { Thread.sleep(10); } catch (InterruptedException stop) { return; }
            }
        }, "tideframe-bake-heap-observer");
        sampler.setDaemon(true); sampler.start();
        long decodeStart = System.nanoTime();
        var screen = ScreenCodec.decode(new String(json, StandardCharsets.UTF_8));
        long decodeEnd = System.nanoTime();
        var raster = HudRaster.render(screen, null, 8);
        long rasterEnd = System.nanoTime();
        if (raster.getWidth() != screen.quality().width() || raster.getHeight() != screen.quality().height())
            throw new AssertionError("Raster resolution differs from selected quality");
        raster = null;
        var assets = HudAssets.create(List.of(screen));
        long bakeEnd = System.nanoTime();
        var pack = PackGenerator.generate(root.resolve(".build-tools/creation-benchmark/" + profile), ImageGlyphs.empty(), assets);
        long packEnd = System.nanoTime();
        sampling.set(false); sampler.interrupt(); sampler.join();
        var entries = assets.entries();
        String font = new String(entries.get("assets/tideframe/font/hud.json"), StandardCharsets.UTF_8);
        int providers = font.split("\\\"type\\\":\\\"bitmap\\\"", -1).length - 1;
        long pngs = entries.keySet().stream().filter(name -> name.endsWith(".png")).count();
        long imagePixels = screen.elements().stream().filter(e -> e.image() != null)
                .mapToLong(e -> (long)e.image().width() * e.image().height()).max().orElse(0);
        String output = String.format(Locale.ROOT,
                "{\n  \"observation\":\"cold local process; no Minecraft client or network transfer\",\n"
                + "  \"profile\":\"%s\",\n  \"width\":%d,\n  \"height\":%d,\n  \"elements\":%d,\n"
                + "  \"largestSourceImagePixels\":%d,\n  \"layoutSha256\":\"%s\",\n"
                + "  \"javaVersion\":\"%s\",\n  \"availableProcessors\":%d,\n  \"heapLimitBytes\":%d,\n"
                + "  \"heapUsedBeforeBytes\":%d,\n  \"sampledPeakHeapBytes\":%d,\n  \"heapSampleIntervalMs\":10,\n"
                + "  \"decodeMs\":%.3f,\n  \"rasterMs\":%.3f,\n  \"bakeMs\":%.3f,\n  \"packWriteMs\":%.3f,\n"
                + "  \"packBytes\":%d,\n  \"packSha256\":\"%s\",\n  \"bitmapProviders\":%d,\n"
                + "  \"uniquePngFiles\":%d,\n  \"finalFrameCodePoints\":%d,\n  \"clientVerified\":false\n}\n",
                profile, screen.quality().width(), screen.quality().height(), screen.elements().size(), imagePixels,
                sha(json), System.getProperty("java.version"), Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory(),
                before, peak.get(), (decodeEnd-decodeStart)/1e6, (rasterEnd-decodeEnd)/1e6, (bakeEnd-rasterEnd)/1e6,
                (packEnd-bakeEnd)/1e6, Files.size(pack.path()), sha(Files.readAllBytes(pack.path())), providers, pngs,
                assets.frame(screen, null, 8).codePointCount(0, assets.frame(screen, null, 8).length()));
        Files.writeString(root.resolve("evidence/creation-v07-benchmark.json"), output);
        System.out.print(output);
    }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
