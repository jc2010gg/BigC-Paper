package dev.linqfy.bigCasares.modules.shop;

public record ShopViewItem(
    String id,
    String name,
    String description,
    String price,
    String iconUrl,
    boolean available
) {
}
