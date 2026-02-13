package dev.voldpix.loomera;

public class LoomeraContext {

    private static JsonProvider jsonProvider;

    private LoomeraContext() {}

    public static JsonProvider getJsonProvider() {
        return jsonProvider;
    }

    public static void setJsonProvider(JsonProvider provider) {
        jsonProvider = provider;
    }
}
