package cs43.group4;

import io.javalin.Javalin;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Random;

public class Main {
    public static void main(String[] args) {
        AtomicInteger counter = new AtomicInteger(0);
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        Random random = new Random();

        // Background scheduler increments counter randomly
        scheduler.scheduleWithFixedDelay(() -> {
            int increment = random.nextInt(5) + 1; // random increment 1–5
            int value = counter.addAndGet(increment);

            if (value > 1000) {
                counter.set(1000); // stop at 1000
                System.out.println("Reached max: 1000");
                scheduler.shutdown(); // stop scheduler
            }
        }, 0, 100, TimeUnit.MILLISECONDS);

        // Javalin app
        Javalin app = Javalin.create(config -> {
            config.http.defaultContentType = "application/json";
            config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.anyHost()));
        }).start(8080);

        app.get("/", ctx -> ctx.json(Map.of("message", "Hello World!")));

        // Endpoint returns current counter value
        app.get("/counter", ctx -> ctx.json(Map.of("value", counter.get())));
    }
}
