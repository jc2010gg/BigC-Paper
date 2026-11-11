package dev.linqfy.bigCasares.modules.geyser;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

public record BedrockShopForm(
    String title,
    String content,
    List<Button> buttons,
    BiConsumer<UUID, Integer> responseHandler
) {
    public BedrockShopForm {
        buttons = List.copyOf(buttons);
    }

    public record Button(String text, String iconUrl) {
    }
}
