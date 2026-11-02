package dev.linqfy.bigCasares.modules.tideframe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TideframePackSettingTest {
    private static void write(Path config, String yaml, long version) throws Exception {
        Files.writeString(config, yaml);
        Files.setLastModifiedTime(config, FileTime.fromMillis(version * 1_000_000L));
    }

    @Test
    void readsPackSendAndOnceOffStaysOffUntilServerRestart(@TempDir Path folder) throws Exception {
        Path config = folder.resolve("config.yml");
        TideframePackSetting setting = new TideframePackSetting(config);
        assertTrue(setting.getAsBoolean(), "no config yet: Tideframe sends its own pack by default");
        write(config, "editor:\n  auto-reload: true\n", 1);
        assertTrue(setting.getAsBoolean(), "without the key: true, as in Tideframe");
        write(config, "pack:\n  send: false\n  port: 8140\n", 2);
        assertFalse(setting.getAsBoolean());
        write(config, "pack:\n  send: true\n", 3);
        assertFalse(setting.getAsBoolean(), "the file says true before Tideframe has reloaded it: still off");
    }

    @Test
    void reconnectingBigCasaresDoesNotForgetTheProvidersOffPackMode(@TempDir Path folder) throws Exception {
        Path config = folder.resolve("config.yml");
        write(config, "pack:\n  send: false\n", 1);
        assertFalse(new TideframePackSetting(config).getAsBoolean());
        write(config, "pack:\n  send: true\n", 2);
        TideframePackSetting reconnected = new TideframePackSetting(folder.resolve(".").resolve("config.yml"));
        assertFalse(reconnected.getAsBoolean(), "BigCasares reload does not reload Tideframe's running pack mode");
    }

    @Test
    void aReplacementThatKeepsTheModificationTimeIsStillRead(@TempDir Path folder) throws Exception {
        Path config = folder.resolve("config.yml");
        TideframePackSetting setting = new TideframePackSetting(config);
        write(config, "pack:\n  send: true\n", 7);
        assertTrue(setting.getAsBoolean());
        write(config, "pack:\n  send: false\n", 7);
        assertFalse(setting.getAsBoolean(), "the same timestamp does not hide the change");
    }

    @Test
    void aConfigThatCannotBeReadCountsAsOff(@TempDir Path folder) throws Exception {
        Path config = folder.resolve("config.yml");
        TideframePackSetting setting = new TideframePackSetting(config);
        write(config, "pack: [send: : broken\n", 1);
        assertFalse(setting.getAsBoolean(), "fails closed");
        write(config, "pack:\n  send: true\n", 2);
        assertTrue(setting.getAsBoolean(), "an unreadable file is not remembered as off once fixed");
    }
}
