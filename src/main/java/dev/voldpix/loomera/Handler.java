package dev.voldpix.loomera;

@FunctionalInterface
public interface Handler {

    void handle(RequestContext ctx) throws Exception;
}
