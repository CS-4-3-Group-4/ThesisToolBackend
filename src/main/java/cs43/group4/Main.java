package cs43.group4;

import io.javalin.Javalin;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        Javalin app = Javalin.create(config -> {
                    config.http.defaultContentType = "application/json";
                    config.bundledPlugins.enableCors(cors -> {
                        cors.addRule(it -> {
                            it.anyHost();
                        });
                    });
                })
                .start(8080);

        app.get("/", ctx -> ctx.json(Map.of("message", "Hello World")));
    }
}
