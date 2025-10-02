package cs43.group4;

import io.javalin.Javalin;

public class Main {
  public static void main(String[] args) {
    Javalin app = Javalin.create().start(7070);
    app.get("/", ctx -> ctx.result("Hello World"));
  }
}
