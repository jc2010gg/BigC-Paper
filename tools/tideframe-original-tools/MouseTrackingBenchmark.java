import dev.linqfy.bigCasares.modules.tideframe.engine.core.HitTester;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.PlaneProjection;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.ScreenCursorInteraction;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Deterministic, offline input replay. No server, packets, renderer or client is measured.
 * From the project root, after the normal build (JDK 25, Java 21 bytecode):
 * javac --release 21 -cp build/classes/java/main -d build/benchmarks tools/MouseTrackingBenchmark.java
 * java -cp "build/classes/java/main;build/benchmarks" MouseTrackingBenchmark evidence/cursor-v09-benchmark.json
 * Use ':' instead of ';' in the classpath on non-Windows systems.
 */
public final class MouseTrackingBenchmark {
    private static final long INTERVAL_NS = 50_000_000L;
    private static final int SAMPLE_COUNT = 240;
    private static final int WARMUP_ROUNDS = 200, MEASURED_ROUNDS = 1000;
    private static final List<HitTester.Rect> TARGETS = List.of(
            new HitTester.Rect("left-upper", -.5, .15, .3, .18),
            new HitTester.Rect("middle-upper", 0, .15, .3, .18),
            new HitTester.Rect("right-upper", .5, .15, .3, .18),
            new HitTester.Rect("left-lower", -.5, -.2, .3, .18),
            new HitTester.Rect("middle-lower", 0, -.2, .3, .18),
            new HitTester.Rect("right-lower", .5, -.2, .3, .18));
    private static volatile double consumed;
    private record Sample(double x, double y, boolean stopped, boolean reversing) { }
    private record Result(int pingMs, double maximumError, double meanError, double stopError, double flickError,
                          int wrongReversal, int hoverClickMismatches, int visualClickMismatches,
                          int selectedFrames, int unselectedFrames) {
        String json() {
            return String.format(Locale.ROOT,
                    "{\"pingMsInput\":%d,\"sampleCount\":%d,\"maxPredictionErrorNormalized\":%.12f,"
                    + "\"meanPredictionErrorNormalized\":%.12f,\"maxStoppedErrorNormalized\":%.12f,"
                    + "\"fastDiagonalFlickMaxErrorNormalized\":%.12f,"
                    + "\"wrongDirectionReversalFrames\":%d,\"hoverClickMismatches\":%d,"
                    + "\"visualClickMismatches\":%d,\"selectedFrames\":%d,\"unselectedFrames\":%d}",
                    pingMs, SAMPLE_COUNT, maximumError, meanError, stopError, flickError, wrongReversal,
                    hoverClickMismatches, visualClickMismatches, selectedFrames, unselectedFrames);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 1) throw new IllegalArgumentException("Optional argument: JSON output path");
        Sample[] trace = trace();
        Result eighty = verify(trace, 80), oneSixty = verify(trace, 160);
        for (int round = 0; round < WARMUP_ROUNDS; round++) replayFour(trace);
        var timer = ManagementFactory.getThreadMXBean();
        boolean cpuAvailable = timer.isCurrentThreadCpuTimeSupported();
        if (cpuAvailable && !timer.isThreadCpuTimeEnabled()) timer.setThreadCpuTimeEnabled(true);
        long cpuStart = cpuAvailable ? timer.getCurrentThreadCpuTime() : 0;
        long wallStart = System.nanoTime();
        for (int round = 0; round < MEASURED_ROUNDS; round++) replayFour(trace);
        long wallNanos = System.nanoTime() - wallStart;
        long cpuNanos = cpuAvailable ? timer.getCurrentThreadCpuTime() - cpuStart : -1;
        long frames = (long) MEASURED_ROUNDS * 4 * SAMPLE_COUNT;
        String json = "{\n"
                + "  \"createdAtUtc\":" + quoted(Instant.now().toString()) + ",\n"
                + "  \"scope\":\"Deterministic 20 Hz local input replay; steady movement, stop, reversal, stop, seeded jitter.\",\n"
                + "  \"javaVersion\":" + quoted(System.getProperty("java.version")) + ",\n"
                + "  \"os\":" + quoted(System.getProperty("os.name") + " " + System.getProperty("os.arch")) + ",\n"
                + "  \"seed\":26109,\"sampleIntervalMs\":50,\"predictionSettingMs\":75,\"snapRadius\":0.08,\n"
                + "  \"scenarios\":[" + eighty.json() + "," + oneSixty.json() + "],\n"
                + "  \"cpu\":{\"warmupTraces\":" + WARMUP_ROUNDS * 4
                + ",\"measuredTraces\":" + MEASURED_ROUNDS * 4 + ",\"measuredFrames\":" + frames
                + ",\"threadCpuTimeNanos\":" + (cpuAvailable ? cpuNanos : "null")
                + ",\"threadCpuNanosPerFrame\":" + (cpuAvailable ? String.format(Locale.ROOT, "%.3f", (double) cpuNanos / frames) : "null")
                + ",\"wallTimeNanos\":" + wallNanos + "},\n"
                + "  \"nativeClientVerified\":false,\"realNetworkLatencyVerified\":false,\n"
                + "  \"limitations\":\"pingMsInput is a numeric predictor input, not a network simulation. Error is distance from supplied actual pose, not unseen local mouse truth. CPU timing excludes Minecraft, networking, font rendering and title delivery; wall time depends on concurrent local work.\"\n"
                + "}\n";
        Path output = Path.of(args.length == 0 ? "evidence/cursor-v09-benchmark.json" : args[0]);
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(output, json, StandardCharsets.UTF_8);
        System.out.print(json);
    }

    private static Sample[] trace() {
        var samples = new Sample[SAMPLE_COUNT];
        var jitter = new Random(26109);
        for (int index = 0; index < samples.length; index++) {
            double x, y;
            if (index < 60) {
                x = -.6 + index * .015;
                y = .1 + index * .002;
            } else if (index < 80) {
                x = .285; y = .218;
            } else if (index < 140) {
                x = .285 - (index - 79) * .012;
                y = .218 - (index - 79) * .003;
            } else if (index < 160) {
                x = -.435; y = .038;
            } else {
                x = -.435 + (jitter.nextDouble() - .5) * .004;
                y = .038 + (jitter.nextDouble() - .5) * .004;
            }
            samples[index] = new Sample(x, y, index >= 60 && index < 80 || index >= 140 && index < 160,
                    index >= 80 && index < 140);
        }
        return samples;
    }

    private static Result verify(Sample[] trace, int pingMs) {
        var free = new ScreenCursorInteraction(0, 0, List.of(), 75, 0);
        var interactive = new ScreenCursorInteraction(0, 0, TARGETS, 75, .08);
        double maxError = 0, sumError = 0, stopError = 0;
        int reversal = 0, hoverMismatch = 0, visualMismatch = 0, selected = 0, unselected = 0;
        for (int index = 0; index < trace.length; index++) {
            var sample = trace[index];
            long now = (index + 1L) * INTERVAL_NS;
            var frame = free.refresh(sample.x() * 35, sample.y() * -25, now, pingMs);
            double error = Math.hypot(frame.cursor().x() - sample.x(), frame.cursor().y() - sample.y());
            maxError = Math.max(maxError, error); sumError += error;
            if (sample.stopped()) stopError = Math.max(stopError, error);
            if (sample.reversing() && frame.cursor().x() > sample.x() + 1e-12) reversal++;
            var clickable = interactive.refresh(sample.x() * 35, sample.y() * -25, now, pingMs);
            if (!clickable.hover().equals(clickable.selection())) hoverMismatch++;
            if (!HitTester.hit(TARGETS, clickable.cursor(), 0).equals(clickable.selection())) visualMismatch++;
            if (clickable.selection().isPresent()) selected++; else unselected++;
        }
        if (maxError > .05 + 1e-12 || stopError > 1e-12 || reversal != 0
                || hoverMismatch != 0 || visualMismatch != 0 || selected == 0 || unselected == 0)
            throw new AssertionError("Input replay safety check failed for ping input " + pingMs
                    + ": maxError=" + maxError + ", stopError=" + stopError + ", reversal=" + reversal
                    + ", hover=" + hoverMismatch + ", visual=" + visualMismatch);
        double flickError = verifyFastFlick(pingMs);
        return new Result(pingMs, maxError, sumError / trace.length, stopError, flickError,
                reversal, hoverMismatch, visualMismatch, selected, unselected);
    }

    private static double verifyFastFlick(int pingMs) {
        var input = new ScreenCursorInteraction(0, 0, List.of(), 75, 0);
        double[] points = {-.7, -.2, .3, .8, .3, -.2, -.7};
        double maximum = 0;
        for (int index = 0; index < points.length; index++) {
            double actual = points[index];
            var frame = input.refresh(actual * 35, actual * -25, (index + 1L) * INTERVAL_NS, pingMs);
            maximum = Math.max(maximum, Math.hypot(frame.cursor().x() - actual, frame.cursor().y() - actual));
        }
        if (maximum > .05 + 1e-12 || maximum < .049)
            throw new AssertionError("Fast flick must exercise, and respect, the .05 vector cap: " + maximum);
        return maximum;
    }

    private static void replayFour(Sample[] trace) {
        consumed = replay(trace, 80, List.of()) + replay(trace, 160, List.of())
                + replay(trace, 80, TARGETS) + replay(trace, 160, TARGETS);
    }

    private static double replay(Sample[] trace, int pingMs, List<HitTester.Rect> targets) {
        var input = new ScreenCursorInteraction(0, 0, targets, 75, .08);
        double checksum = 0;
        for (int index = 0; index < trace.length; index++) {
            var sample = trace[index];
            var frame = input.refresh(sample.x() * 35, sample.y() * -25, (index + 1L) * INTERVAL_NS, pingMs);
            checksum += frame.cursor().x() + frame.cursor().y() + (frame.selection().isPresent() ? 1 : 0);
        }
        return checksum;
    }

    private static String quoted(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
