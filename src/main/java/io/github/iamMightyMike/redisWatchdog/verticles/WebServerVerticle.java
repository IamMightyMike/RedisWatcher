package io.github.iamMightyMike.redisWatchdog.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;

public class WebServerVerticle extends AbstractVerticle {

    @Override
    public void start() throws Exception {

        System.out.println("WebServerVerticle start");
        EventBus eventBus = vertx.eventBus();

        //Router router = Router.router(vertx);
        //router.route().handler(StaticHandler.create());

        eventBus.consumer("redis.data.update", this::handleMessage);

        //vertx.createHttpServer().requestHandler(router).listen(8080, res -> {
        //    if (res.succeeded()) {
        //        System.out.println("Web server is running on port 8080");
        //    } else {
        //        System.err.println("Failed to start web server: " + res.cause().getMessage());
        //    }
        //});

        System.out.println("WebServerVerticle start FIN");

    }

    private void handleMessage(Message<JsonObject> message){

        JsonObject receivedObject = message.body();

        if(receivedObject != null){
            System.out.println(" Key -> " + receivedObject.getString("key"));
            System.out.println(" newField -> " + receivedObject.getString("newField"));
            System.out.println(" oldField -> " + receivedObject.getString("oldField"));
        }




    }

}
