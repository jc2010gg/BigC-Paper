package dev.tideframe.edit;
import dev.tideframe.core.Screen;
import java.io.IOException;
public interface ScreenStorage {
    Screen load(String id) throws IOException;
    void save(Screen screen) throws IOException;
}
