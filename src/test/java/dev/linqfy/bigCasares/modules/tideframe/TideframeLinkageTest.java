package dev.linqfy.bigCasares.modules.tideframe;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.testsupport.CompiledClass;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BigCasares must load, and fall back to its own menus, on a server without Tideframe: only the two classes that
 * TideframeLink loads by reflection, after finding the Tideframe plugin enabled, may name Tideframe's types.
 */
class TideframeLinkageTest {
    private static final Set<String> BRIDGES = Set.of(
        "dev/linqfy/bigCasares/modules/tideframe/TideframeApiBridge",
        "dev/linqfy/bigCasares/modules/tideframe/TideframeEventRelay");

    @Test
    void onlyTheTwoBridgesNameTideframeTypes() throws Exception {
        Path classes = Path.of(BigCasares.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertTrue(Files.isDirectory(classes), "the main classes are read from the build's class folder");
        Set<String> linked = new TreeSet<>();
        int checked = 0;
        try (Stream<Path> files = Files.walk(classes)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                String name = classes.relativize(file).toString().replace('\\', '/').replaceAll("\\.class$", "");
                CompiledClass compiled = CompiledClass.of(BigCasares.class, name.replace('/', '.'));
                checked++;
                if (compiled.referencedClasses().stream().anyMatch(type -> type.startsWith("dev/tideframe/"))) linked.add(name);
            }
        }
        assertTrue(checked > 500, "the whole plugin was checked: " + checked);
        assertEquals(new TreeSet<>(BRIDGES), linked);
    }

    @Test
    void withoutTideframeTheLinkFailsInAWayTheModuleCatches() {
        // The test classpath has no Tideframe, like a server without it: TideframeUiModule catches exactly these.
        var server = (org.bukkit.Server) java.lang.reflect.Proxy.newProxyInstance(org.bukkit.Server.class.getClassLoader(),
            new Class<?>[]{org.bukkit.Server.class}, (proxy, method, args) -> {
                if (method.getName().equals("getServicesManager")) {
                    return java.lang.reflect.Proxy.newProxyInstance(org.bukkit.plugin.ServicesManager.class.getClassLoader(),
                        new Class<?>[]{org.bukkit.plugin.ServicesManager.class}, (p, m, a) -> null);
                }
                throw new AssertionError("Unexpected server call: " + method.getName());
            });
        Throwable failure = org.junit.jupiter.api.Assertions.assertThrows(Throwable.class, () -> TideframeLink.bridge(server));
        assertTrue(failure instanceof ReflectiveOperationException || failure instanceof LinkageError, failure.toString());
        Throwable relay = org.junit.jupiter.api.Assertions.assertThrows(Throwable.class, () -> TideframeLink.relay(new TideframeEventSink() {
            @Override public boolean allowOpen(org.bukkit.entity.Player player, String screenId) { return true; }
            @Override public boolean click(org.bukkit.entity.Player player, TideframeClick click) { return false; }
            @Override public void closed(org.bukkit.entity.Player player, String screenId, boolean wholeSession) { }
        }));
        assertTrue(relay instanceof ReflectiveOperationException || relay instanceof LinkageError, relay.toString());
    }

    @Test
    void theBridgesUseTheRealServiceAndEvents() throws Exception {
        var bridge = CompiledClass.of(BigCasares.class, "dev.linqfy.bigCasares.modules.tideframe.TideframeApiBridge");
        assertTrue(bridge.referencedClasses().contains("dev/tideframe/TideframeApi"));
        var relay = CompiledClass.of(BigCasares.class, "dev.linqfy.bigCasares.modules.tideframe.TideframeEventRelay");
        assertTrue(relay.referencedClasses().containsAll(List.of("dev/tideframe/TideframeClickEvent", "dev/tideframe/TideframeCloseEvent",
            "dev/tideframe/TideframeOpenEvent")));
        assertTrue(relay.calls("dev/tideframe/TideframeClickEvent", "setCancelled"), "a handled click is cancelled in Tideframe");
        assertTrue(relay.calls("dev/tideframe/TideframeOpenEvent", "setCancelled"), "an opening BigCasares did not ask for is cancelled");
    }

    /** What the bridges check while connecting, so a different Tideframe falls back at once instead of failing later. */
    interface Api {
        boolean isPackLoaded(String player);

        java.util.Optional<String> currentScreen(String player);
    }

    @Test
    void aDifferentApiIsRejectedWhileConnecting() throws Exception {
        TideframeApiShape.require(Api.class, boolean.class, "isPackLoaded", String.class);
        TideframeApiShape.require(Api.class, java.util.Optional.class, "currentScreen", String.class);
        org.junit.jupiter.api.Assertions.assertThrows(NoSuchMethodException.class,
            () -> TideframeApiShape.require(Api.class, void.class, "openWhenReady", String.class), "a missing method");
        org.junit.jupiter.api.Assertions.assertThrows(NoSuchMethodException.class,
            () -> TideframeApiShape.require(Api.class, boolean.class, "isPackLoaded", Integer.class), "other parameters");
        org.junit.jupiter.api.Assertions.assertThrows(NoSuchMethodException.class,
            () -> TideframeApiShape.require(Api.class, String.class, "currentScreen", String.class), "another return type would fail at the call");
    }
}
