package dev.linqfy.bigCasares.modules.tideframe;

import org.bukkit.Server;
import org.bukkit.event.Listener;

/**
 * Loads the two classes that name Tideframe types by reflection, so nothing else in BigCasares links against Tideframe
 * and the plugin loads (and falls back to its own menus) when Tideframe is missing or older.
 */
final class TideframeLink {
    private static final String BRIDGE = "dev.linqfy.bigCasares.modules.tideframe.TideframeApiBridge";
    private static final String RELAY = "dev.linqfy.bigCasares.modules.tideframe.TideframeEventRelay";

    private TideframeLink() {
    }

    /** The bridge over Tideframe's service, or null when it is not registered. Throws when the API does not match. */
    static TideframeBridge bridge(Server server) throws ReflectiveOperationException, LinkageError {
        Class<?> type = Class.forName(BRIDGE, true, TideframeLink.class.getClassLoader());
        return (TideframeBridge) type.getDeclaredMethod("connect", Server.class).invoke(null, server);
    }

    static Listener relay(TideframeEventSink sink) throws ReflectiveOperationException, LinkageError {
        Class<?> type = Class.forName(RELAY, true, TideframeLink.class.getClassLoader());
        return (Listener) type.getDeclaredMethod("create", TideframeEventSink.class).invoke(null, sink);
    }
}
