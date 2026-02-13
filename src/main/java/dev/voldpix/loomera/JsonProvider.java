package dev.voldpix.loomera;

public interface JsonProvider {
    String toJson(Object obj);
    <T> T fromJson(String json, Class<T> type);
}
