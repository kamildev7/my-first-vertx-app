package vertx.app.example.first;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;

/**
 * @author kamildev7 on 2018-08-22.
 */
public class MyFirstVerticle extends AbstractVerticle {

    @Override
    public void start(Future<Void> future) {
        vertx
                .createHttpServer()
                .requestHandler(r-> {
                    r.response().end("<h1>Hello from my first " +
                            "Vert.x 3 application</h1>");
                })
                .listen(8080, httpServerAsyncResult -> {
                    if (httpServerAsyncResult.succeeded()) {
                        future.complete();
                    } else {
                        future.fail(httpServerAsyncResult.cause());
                    }
                });
    }
}
