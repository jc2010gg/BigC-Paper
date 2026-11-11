package dev.linqfy.bigCasares.modules.schizophrenia;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EpisodeTimelineTest {

    @Test
    void runsCuesWhenDueAndAllowsChaining() {
        EpisodeTimeline timeline = new EpisodeTimeline();
        List<String> log = new ArrayList<>();
        timeline.at(10, () -> {
            log.add("step");
            timeline.at(12, () -> log.add("next"));
        });

        timeline.runDue(9, failure -> { });
        assertEquals(List.of(), log);
        timeline.runDue(10, failure -> { });
        timeline.runDue(12, failure -> { });
        assertEquals(List.of("step", "next"), log);
        assertEquals(0, timeline.size());
    }

    @Test
    void flushDropsScaresButAlwaysRestoresClientState() {
        EpisodeTimeline timeline = new EpisodeTimeline();
        List<String> log = new ArrayList<>();
        timeline.at(100, () -> log.add("scare"));
        timeline.restoreAt(200, () -> log.add("restore"));

        timeline.flush(failure -> { });

        assertEquals(List.of("restore"), log);
        assertEquals(0, timeline.size());
    }

    @Test
    void oneBrokenCueDoesNotStopTheOthers() {
        EpisodeTimeline timeline = new EpisodeTimeline();
        List<String> log = new ArrayList<>();
        List<RuntimeException> failures = new ArrayList<>();
        timeline.at(1, () -> {
            throw new IllegalStateException("boom");
        });
        timeline.at(1, () -> log.add("ok"));

        timeline.runDue(1, failures::add);

        assertEquals(List.of("ok"), log);
        assertEquals(1, failures.size());
    }
}
