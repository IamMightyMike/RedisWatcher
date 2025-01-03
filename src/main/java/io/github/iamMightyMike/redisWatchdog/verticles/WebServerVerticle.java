package io.github.iamMightyMike.redisWatchdog.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;

public class WebServerVerticle extends AbstractVerticle {

    private static final String SSE_EVENT = "redis.data.update";


    @Override
    public void start() throws Exception {

        System.out.println("WebServerVerticle start INICIO");
        EventBus eventBus = vertx.eventBus();
        Router router = Router.router(vertx);
        router.route().handler(StaticHandler.create().setWebRoot("webroot"));

        //Consumidor de prueba con la única finalidad dep robar a imprimir por pantalla los eventos recibidos por el bus
        eventBus.consumer(SSE_EVENT, this::handleMessage);

        router.get("/events").handler(ctx -> {
            // Setear el tipo de contenido para enviar eventos de actualización de Redis al Front
            ctx.response()
                    .putHeader("Content-Type", "text/event-stream")
                    .putHeader("Cache-Control", "no-cache")
                    .putHeader("Connection", "keep-alive")
                    .putHeader("Transfer-Encoding", "chunked");

            // Creación de un consumidor para enviar eventos al front
            eventBus.consumer(SSE_EVENT,  message -> {

                Object messageBody = message.body();
                if(messageBody != null){
                    JsonObject data = (JsonObject)message.body();
                    // Write SSE message (with 'data' field) to the client
                    ctx.response().write("data: " + data.encode() + "\n\n");
                }


            });
        });


        vertx.createHttpServer().requestHandler(router).listen(8081, res -> {
            if (res.succeeded()) {
                System.out.println("Web server ejecutándose en puerto 8081 8081");
            } else {
                System.err.println("Error al arrancar el Web Server: " + res.cause().getMessage());
            }
        });

        System.out.println("WebServerVerticle start FIN");

    }

    private void handleMessage(Message<JsonObject> message){

        JsonObject receivedObject = message.body();
        if(receivedObject != null){
            System.out.println(" Key -> " + receivedObject.getString("key"));
            System.out.println("    newField -> " + receivedObject.getString("newField"));
            System.out.println("    oldField -> " + receivedObject.getString("oldField"));
        }
    }
}
