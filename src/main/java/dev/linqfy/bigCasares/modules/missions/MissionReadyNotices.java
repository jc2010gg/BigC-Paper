package dev.linqfy.bigCasares.modules.missions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which missions to announce as ready: each one once per period, even when its completion turns off and on again
 * (a passive mission while the player drops and picks up the item it asks for). A new period, with a new reset time,
 * announces again.
 */
final class MissionReadyNotices {
    private final Map<UUID, Set<String>> announced = new ConcurrentHashMap<>();

    /** The missions newly ready from {@code before} to {@code after} that this player was not told about this period. */
    List<MissionDefinition> toAnnounce(MissionPlayerState before, MissionPlayerState after) {
        Set<String> told = announced.computeIfAbsent(after.playerId(), ignored -> ConcurrentHashMap.newKeySet());
        List<MissionDefinition> fresh = new ArrayList<>();
        for (MissionDefinition ready : MissionOrder.newlyReady(before, after)) {
            boolean daily = after.dailyAssignments().containsKey(ready.id());
            String key = (daily ? "d:" + after.dailyResetsAt() : "w:" + after.weeklyResetsAt()) + ':' + ready.id();
            if (told.add(key)) {
                fresh.add(ready);
            }
        }
        return fresh;
    }

    /** The player left: forget them (at worst, a passive mission is announced once more on their return). */
    void forget(UUID playerId) {
        announced.remove(playerId);
    }

    void clear() {
        announced.clear();
    }
}
