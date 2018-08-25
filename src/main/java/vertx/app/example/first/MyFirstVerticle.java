package vertx.app.example.first;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author kamildev7 on 2018-08-22.
 */
public class MyFirstVerticle extends AbstractVerticle {

    private JDBCClient jdbc;

    @Override
    public void start(Future<Void> future) {

        jdbc = JDBCClient.createShared(vertx, config(), "My-Whisky-Collection");

        startBackend(
            (connection) -> createSomeData(connection,
                (nothing) -> startWebApp(
                    (http) -> completeStartup(http, future)
                ), future
            ), future);
    }

    private void startBackend(Handler<AsyncResult<SQLConnection>> next, Future<Void> future) {
        jdbc.getConnection(ar -> {
            if (ar.failed()) {
                future.fail(ar.cause());
            } else {
                next.handle(Future.succeededFuture(ar.result()));
            }
        });
    }

    private void startWebApp(Handler<AsyncResult<HttpServer>> next) {
        Router router = Router.router(vertx);

        router.route("/").handler(routingContext -> {
            HttpServerResponse response = routingContext.response();
            response
                .putHeader("content-type", "text/html")
                .end("<h1>Hello from my first Vert.x 3 application</h1>");
        });

        // Serve static resources from the /assets directory
        router.route("/assets/*").handler(StaticHandler.create("assets"));

        router.get("/api/whiskies").handler(this::getAll);
        router.route("/api/whiskies*").handler(BodyHandler.create());
        router.post("/api/whiskies").handler(this::addOne);
        router.put("/api/whiskies/:id").handler(this::updateOne);
        router.get("/api/whiskies/:id").handler(this::getOne);
        router.delete("/api/whiskies/:id").handler(this::deleteOne);

        vertx
            .createHttpServer()
            .requestHandler(router::accept)
            .listen(
                // Retrieve the port from the configuration,
                // default to 8080.
                config().getInteger("http.port", 8080),
                next::handle
            );
    }

    private void completeStartup(AsyncResult<HttpServer> http, Future<Void> future) {
        if (http.succeeded()) {
            future.complete();
        } else {
            future.fail(http.cause());
        }
    }

    @Override
    public void stop() throws Exception {
        // Close the JDBC client.
        jdbc.close();
    }


    private void getAll(RoutingContext routingContext) {
        jdbc.getConnection(ar -> {
            SQLConnection connection = ar.result();
            connection.query("SELECT * FROM Whisky", result -> {
                List<Whisky> whiskies = result.result().getRows().stream().map(Whisky::new).collect(Collectors.toList
                    ());
                routingContext.response()
                    .putHeader("content-type", "application/json; charset=utf-8")
                    .end(Json.encodePrettily(whiskies));
                connection.close();
            });
        });
    }

    private void getOne(RoutingContext routingContext) {
        String id = routingContext.request().getParam("id");
        if (id == null) {
            routingContext.response().setStatusCode(400).end();
        } else {
            jdbc.getConnection(ar -> {
                // Read the request's content and create an instance of Whisky.
                SQLConnection connection = ar.result();
                select(id, connection, result -> {
                    if (result.failed()) {
                        routingContext.response().setStatusCode(404).end();
                    } else {
                        routingContext.response()
                            .setStatusCode(200)
                            .putHeader("content-type", "application/json; charset=utf-8")
                            .end(Json.encodePrettily(result.result()));
                    }
                    connection.close();
                });
            });
        }
    }

    private void addOne(RoutingContext routingContext) {
        jdbc.getConnection(sqlConnectionAsyncResult -> {
            final Whisky whisky = Json.decodeValue(routingContext.getBodyAsString(), Whisky.class);
            SQLConnection connection = sqlConnectionAsyncResult.result();
            insert(whisky, connection, (r) ->
                routingContext.response()
                    .setStatusCode(201)
                    .putHeader("content-type", "application/json; charset=utf-8")
                    .end(Json.encodePrettily(r.result())));
            connection.close();
        });
    }

    private void updateOne(RoutingContext routingContext) {
        final String id = routingContext.request().getParam("id");
        JsonObject json = routingContext.getBodyAsJson();
        if (id == null || json == null) {
            routingContext.response().setStatusCode(400).end();
        } else {
            jdbc.getConnection(ar -> {
                SQLConnection connection = ar.result();
                update(id, json, connection, (whisky) -> {
                    if (whisky.failed()) {
                        routingContext.response().setStatusCode(404).end();
                    } else {
                        routingContext.response()
                            .putHeader("content-type", "application-json; charset=utf-8")
                            .end(Json.encodePrettily(whisky.result()));
                    }
                    connection.close();
                });
            });
        }
    }

    private void deleteOne(RoutingContext routingContext) {
        String id = routingContext.request().getParam("id");
        if (id == null) {
            routingContext.response().setStatusCode(400).end();
        } else {
            jdbc.getConnection(ar -> {
                SQLConnection connection = ar.result();
                connection.execute("DELETE FROM Whisky WHERE id='" + id + "'",
                    voidAsyncResult -> {
                        routingContext.response().setStatusCode(204).end();
                        connection.close();
                    });
            });
        }
    }

    private void select(String id, SQLConnection connection, Handler<AsyncResult<Whisky>> resultHandler) {
        String sql = "SELECT * FROM Whisky WHERE id=?";
        connection.queryWithParams(sql, new JsonArray().add(id), ar -> {
            if (ar.failed()) {
                resultHandler.handle(Future.failedFuture("Whisky not found"));
            } else {
                if (ar.result().getNumRows() >= 1) {
                    resultHandler.handle(Future.succeededFuture(new Whisky(ar.result().getRows().get(0))));
                } else {
                    resultHandler.handle(Future.failedFuture("Whisky not found"));
                }
            }
        });
    }

    private void update(String id, JsonObject content, SQLConnection connection,
                        Handler<AsyncResult<Whisky>> resultHandler) {
        String sql = "UPDATE Whisky SET name=?, origin=? WHERE id=?";
        connection.updateWithParams(sql,
            new JsonArray().add(content.getString("name")).add(content.getString("origin")).add(id),
            updateResultAsyncResult -> {
                if (updateResultAsyncResult.failed()) {
                    resultHandler.handle(Future.failedFuture("Cannot update the whisky"));
                    return;
                }
                if (updateResultAsyncResult.result().getUpdated() == 0) {
                    resultHandler.handle(Future.failedFuture("Whisky not found"));
                    return;
                }
                resultHandler.handle(
                    Future.succeededFuture(new Whisky(Integer.valueOf(id),
                        content.getString("name"), content.getString("origin"))));
            });
    }

    private void insert(Whisky whisky, SQLConnection connection, Handler<AsyncResult<Whisky>> next) {
        String sql = "INSERT INTO Whisky (name, origin) VALUES ?, ?";
        connection.updateWithParams(sql,
            new JsonArray().add(whisky.getName()).add(whisky.getOrigin()),
            (ar) -> {
                if (ar.failed()) {
                    next.handle(Future.failedFuture(ar.cause()));
                    return;
                }
                UpdateResult result = ar.result();
                // Build a new whisky instance with the generated id.
                Whisky newWhisky = new Whisky(result.getKeys().getInteger(0), whisky.getName(), whisky.getOrigin());
                next.handle(Future.succeededFuture(newWhisky));
            });
    }

    private void createSomeData(AsyncResult<SQLConnection> result, Handler<AsyncResult<Void>> next, Future<Void>
        future) {
        if (result.failed()) {
            future.fail(result.cause());
        } else {
            SQLConnection connection = result.result();
            connection.execute(
                "CREATE TABLE IF NOT EXISTS Whisky (id INTEGER IDENTITY, name varchar(100), " +
                    "origin varchar(100))",
                ar -> {
                    if (ar.failed()) {
                        future.fail(ar.cause());
                        connection.close();
                        return;
                    }
                    connection.query("SELECT * FROM Whisky", select -> {
                        if (select.failed()) {
                            future.fail(ar.cause());
                            connection.close();
                            return;
                        }
                        if (select.result().getNumRows() == 0) {
                            insert(
                                new Whisky("Bowmore 15 Years Laimrig", "Scotland, Islay"),
                                connection,
                                (v) -> insert(new Whisky("Talisker 57° North", "Scotland, Island"),
                                    connection,
                                    (r) -> {
                                        next.handle(Future.<Void>succeededFuture());
                                        connection.close();
                                    }));
                        } else {
                            next.handle(Future.<Void>succeededFuture());
                            connection.close();
                        }
                    });
                });
        }
    }
}
