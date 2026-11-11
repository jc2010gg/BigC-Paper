package dev.linqfy.bigCasares.modules.schizophrenia;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

/** Weighted choice among the hallucinations of one beat unlocked at the current intensity. */
final class HallucinationPicker {

    private final Set<Hallucination> disabled;

    HallucinationPicker(Set<Hallucination> disabled) {
        this.disabled = Set.copyOf(disabled);
    }

    List<Hallucination> eligible(int intensity, Hallucination.Beat beat) {
        List<Hallucination> eligible = new ArrayList<>();
        for (Hallucination hallucination : Hallucination.values()) {
            if (hallucination.beat() == beat && hallucination.minIntensity() <= intensity
                && !disabled.contains(hallucination)) {
                eligible.add(hallucination);
            }
        }
        return eligible;
    }

    Optional<Hallucination> pick(int intensity, Hallucination.Beat beat, Hallucination previous, RandomGenerator random) {
        List<Hallucination> eligible = eligible(intensity, beat);
        if (eligible.size() > 1) {
            eligible.remove(previous);
        }
        int total = eligible.stream().mapToInt(Hallucination::weight).sum();
        if (total <= 0) {
            return Optional.empty();
        }
        int roll = random.nextInt(total);
        for (Hallucination hallucination : eligible) {
            roll -= hallucination.weight();
            if (roll < 0) {
                return Optional.of(hallucination);
            }
        }
        return Optional.of(eligible.getLast());
    }
}
