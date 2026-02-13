package dev.voldpix.loomera;

public class LoomeraTest {

    public static void main(String[] args) {
        Loomera app = new Loomera();
        app.get("/api/users", ctx -> {
            ctx.setStatus(200).result("test");

            System.out.println(ctx.getQueryParam("q"));
        });

        app.get("/api/users/*", ctx -> {
            ctx.setStatus(200).result("wildcard test");
            System.out.println(ctx.getWildcardPath());
        });
        app.port(8099).start();
    }
}
