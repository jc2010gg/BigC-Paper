package dev.linqfy.bigCasares.modules.shop;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ShopCatalog {

    private final List<ShopCategory> categories;
    private final Map<String, ShopCategory> categoriesById;

    public ShopCatalog(List<ShopCategory> categories) {
        this.categories = List.copyOf(categories);
        this.categoriesById = categories.stream().collect(
            java.util.stream.Collectors.toMap(
                category -> normalize(category.id()),
                category -> category,
                (left, right) -> left,
                java.util.LinkedHashMap::new
            )
        );
    }

    public List<ShopCategory> categories() {
        return categories;
    }

    public Optional<ShopCategory> category(String id) {
        return Optional.ofNullable(categoriesById.get(normalize(id)));
    }

    public Optional<ShopEntry> entry(String categoryId, String entryId) {
        String normalizedEntryId = normalize(entryId);
        return category(categoryId).flatMap(category -> category.entries().stream()
            .filter(entry -> normalize(entry.id()).equals(normalizedEntryId))
            .findFirst());
    }

    private String normalize(String id) {
        return id.toLowerCase(Locale.ROOT).trim();
    }
}
