package io.github.iamMightyMike.redisWatchdog.client;

import io.github.iamMightyMike.redisWatchdog.handler.RedisMessageHandler;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.redis.client.*;

public class RedisClientFactory {

    private final RedisOptions options;

    public RedisClientFactory(RedisOptions options) {
        this.options = options;
    }

    public Future<RedisConnection> createClient(Vertx vertx, RedisMessageHandler messageHandler) {

        return Redis.createClient(vertx, options)
                .connect()
                .onSuccess(connection -> {
                    System.out.println("Conectadiiisimo");

                    connection.send(Request.cmd(Command.PSUBSCRIBE).arg("*"))
                            .onSuccess(response -> System.out.println("Subscribed to Redis key events"))
                            .onFailure(error -> System.err.println("Failed to subscribe: " + error.getMessage()));


                    connection.handler(message -> {messageHandler.handleMessage(message,connection);});


                })
                .onFailure(error ->{
                    System.out.println("No mi pana " + error.getMessage());
                });

    }

}
