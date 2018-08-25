package vertx.app.example.first;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;

/**
 * @author kamildev7 on 2018-08-22.
 */
public class MyFirstVerticle extends AbstractVerticle {

    @Override
    public void start(Future<Void> future) {

        Router router = Router.router(vertx);

        router.route("/").handler(routingContext -> {
            HttpServerResponse response = routingContext.response();
            response
                .putHeader("content-type", "text/html")
                .end("<h1>Hello from my first Vert.x 3 application</h1>");
        });

        // Serve static resources from the /assets directory
        router.route("/assets/*").handler(StaticHandler.create("assets"));

        vertx
            .createHttpServer()
            .requestHandler(router::accept)
            .listen(
                // Retrieve the port from the configuration,
                // default to 8080.
                config().getInteger("http.port", 8080),
                httpServerAsyncResult -> {
                    if (httpServerAsyncResult.succeeded()) {
                        future.complete();
                    } else {
                        future.fail(httpServerAsyncResult.cause());
                    }
                });
    }
}
