package dev.tideframe.core.logic;

/** Test helper: the codec's own bounded JSON reader. */
final class Json {
    private Json() { }
    static Object parse(String json) { return dev.tideframe.edit.ScreenCodec.parse(json); }
}
