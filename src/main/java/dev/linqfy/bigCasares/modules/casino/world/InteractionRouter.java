package dev.linqfy.bigCasares.modules.casino.world;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Click handlers of casino entities, looked up by entity id. */
public final class InteractionRouter {

    private final Map<UUID, Consumer<Player>> handlers = new HashMap<>();

    public void bind(Entity entity, Consumer<Player> handler) {
        handlers.put(entity.getUniqueId(), handler);
    }

    public void unbind(Entity entity) {
        handlers.remove(entity.getUniqueId());
    }

    public void unbindAll(EntityGroup group) {
        group.entities().forEach(this::unbind);
    }

    public boolean dispatch(Entity clicked, Player player) {
        Consumer<Player> handler = handlers.get(clicked.getUniqueId());
        if (handler == null) {
            return false;
        }
        handler.accept(player);
        return true;
    }

    public void clear() {
        handlers.clear();
    }
}
