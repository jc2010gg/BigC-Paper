package dev.linqfy.bigCasares.modules.keepinventorytotem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** Decides which inventory slots hold totems beyond the carry limit. Generic so it is tested without Bukkit. */
final class KeepInventoryTotemCarryLimit {

    private KeepInventoryTotemCarryLimit() {
    }

    static <T> int count(T[] contents, Predicate<T> isTotem, ToIntFunction<T> amount) {
        int total = 0;
        for (T stack : contents) {
            if (stack != null && isTotem.test(stack)) {
                total += amount.applyAsInt(stack);
            }
        }
        return total;
    }

    /** First slot that holds a totem, or {@code -1}. */
    static <T> int firstSlot(T[] contents, Predicate<T> isTotem) {
        for (int slot = 0; slot < contents.length; slot++) {
            if (contents[slot] != null && isTotem.test(contents[slot])) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * How many totems to take out of each slot so that at most {@code limit} remain, keeping the earliest
     * slots. Returned as {@code [slot, amountToRemove]} pairs.
     */
    static <T> List<int[]> excess(T[] contents, Predicate<T> isTotem, ToIntFunction<T> amount, int limit) {
        List<int[]> removals = new ArrayList<>();
        int kept = 0;
        for (int slot = 0; slot < contents.length; slot++) {
            T stack = contents[slot];
            if (stack == null || !isTotem.test(stack)) {
                continue;
            }
            int stackAmount = amount.applyAsInt(stack);
            int keepHere = Math.max(0, Math.min(stackAmount, limit - kept));
            kept += keepHere;
            if (stackAmount > keepHere) {
                removals.add(new int[] {slot, stackAmount - keepHere});
            }
        }
        return removals;
    }
}
