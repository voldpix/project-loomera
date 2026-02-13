package dev.voldpix.loomera;

@FunctionalInterface
public interface ExceptionHandler<T extends Exception> {
    void handle(RequestContext ctx, T exception);
}
