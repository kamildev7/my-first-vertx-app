package vertx.app.example.first;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.ServerSocket;

/**
 * @author kamildev7 on 2018-08-22.
 */
@RunWith(VertxUnitRunner.class)
public class MyFirstVerticleTest {

    private Vertx vertx;
    private Integer port;

    @Before
    public void setUp(TestContext context) throws Exception {
        vertx = Vertx.vertx();

//        port = 8081;
        //listen on the 'test' port (randomly picked)
        ServerSocket socket = new ServerSocket(0);
        port = socket.getLocalPort();
        socket.close();

        DeploymentOptions options = new DeploymentOptions()
            .setConfig(new JsonObject().put("http.port", port));

        vertx.deployVerticle(MyFirstVerticle.class.getName(), options,
            context.asyncAssertSuccess());
    }

    @After
    public void tearDown(TestContext context) throws Exception {
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void startTest(TestContext context) {
        final Async async = context.async();

        vertx.createHttpClient().getNow(port, "localhost", "/",
            httpClientResponse -> {
                httpClientResponse.handler(body -> {
                    context.assertTrue(body.toString().contains("Hello"));
                    async.complete();
                });
            });
    }
}