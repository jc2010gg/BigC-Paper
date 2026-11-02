package dev.linqfy.bigCasares.modules.tideframe;

import dev.tideframe.TideframeApi;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * {@link TideframeBridge} over Tideframe's registered {@code TideframeApi} service. One of the two classes that name
 * Tideframe types (with {@link TideframeEventRelay}): it is only loaded through {@link TideframeLink} once the Tideframe
 * plugin is enabled, so BigCasares runs without Tideframe.
 */
final class TideframeApiBridge implements TideframeBridge {
    private final TideframeApi api;

    private TideframeApiBridge(TideframeApi api) {
        this.api = Objects.requireNonNull(api, "api");
    }

    /** The bridge, or null when Tideframe has not registered its service. Throws when its API lacks a method used here. */
    static TideframeBridge connect(Server server) throws NoSuchMethodException {
        TideframeApiShape.require(TideframeApi.class, Set.class, "screenIds");
        TideframeApiShape.require(TideframeApi.class, boolean.class, "isPackLoaded", Player.class);
        TideframeApiShape.require(TideframeApi.class, CompletableFuture.class, "openWhenReady", Player.class, String.class);
        TideframeApiShape.require(TideframeApi.class, Optional.class, "currentScreen", Player.class);
        TideframeApiShape.require(TideframeApi.class, void.class, "closeScreen", Player.class);
        TideframeApiShape.require(TideframeApi.class, void.class, "setVariable", Player.class, String.class, String.class);
        TideframeApiShape.require(TideframeApi.class, void.class, "setList", Player.class, String.class, List.class);
        TideframeApi api = server.getServicesManager().load(TideframeApi.class);
        return api == null ? null : new TideframeApiBridge(api);
    }

    @Override
    public Set<String> screenIds() {
        return api.screenIds();
    }

    @Override
    public boolean isPackLoaded(Player player) {
        return api.isPackLoaded(player);
    }

    @Override
    public CompletableFuture<Boolean> openWhenReady(Player player, String screenId) {
        return api.openWhenReady(player, screenId);
    }

    @Override
    public Optional<String> currentScreen(Player player) {
        return api.currentScreen(player);
    }

    @Override
    public void closeScreen(Player player) {
        api.closeScreen(player);
    }

    @Override
    public void setVariable(Player player, String name, String value) {
        api.setVariable(player, name, value);
    }

    @Override
    public void setList(Player player, String source, List<Map<String, String>> rows) {
        api.setList(player, source, rows);
    }
}
