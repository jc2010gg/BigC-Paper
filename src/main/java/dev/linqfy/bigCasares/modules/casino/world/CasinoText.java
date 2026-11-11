package dev.linqfy.bigCasares.modules.casino.world;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class CasinoText {

    private CasinoText() {
    }

    /** Legacy {@code §} string to a non-italic component (item names are italic by default). */
    public static Component legacy(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text)
            .decoration(TextDecoration.ITALIC, false);
    }
}
