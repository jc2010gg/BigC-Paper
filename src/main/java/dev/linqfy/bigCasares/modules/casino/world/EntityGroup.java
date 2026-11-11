package dev.linqfy.bigCasares.modules.casino.world;

import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/** Entities owned by one attraction (or one piece of it) that are removed together. */
public final class EntityGroup {

    private final List<Entity> entities = new ArrayList<>();

    public <T extends Entity> T add(T entity) {
        entities.add(entity);
        return entity;
    }

    public List<Entity> entities() {
        return List.copyOf(entities);
    }

    public boolean isEmpty() {
        return entities.isEmpty();
    }

    public void removeAll() {
        for (Entity entity : entities) {
            entity.eject();
            if (entity.isValid()) {
                entity.remove();
            }
        }
        entities.clear();
    }
}
