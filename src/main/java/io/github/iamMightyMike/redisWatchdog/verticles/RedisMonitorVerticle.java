package io.github.iamMightyMike.redisWatchdog.verticles;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iamMightyMike.redisWatchdog.handler.RedisMessageHandler;
import io.github.iamMightyMike.redisWatchdog.model.RedisEvent;
import io.github.iamMightyMike.redisWatchdog.utils.PropertiesLoader;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.client.*;

import java.awt.*;
import java.util.*;
import java.util.stream.Collectors;

public class RedisMonitorVerticle extends AbstractVerticle {


    private HashMap<String, Object> redisData;
    private ObjectMapper mapper = new ObjectMapper();
    private RedisMessageHandler redisMessageHandler;

    EventBus eventBus;


    public RedisMonitorVerticle(HashMap<String, Object> redisData) {
        super();
        this.redisData = redisData;

    }

    @Override
    public void start() {

        System.out.println("RedisMonitorVerticle start INICIO");


        eventBus = vertx.eventBus();
        this.redisMessageHandler = new RedisMessageHandler(eventBus, redisData);

        Properties properties = PropertiesLoader.load("application.properties");

        String host = properties.getProperty("redis.host", "127.0.0.1");
        int port = Integer.parseInt(properties.getProperty("redis.port", "6379"));
        String password = properties.getProperty("redis.password", null);

        //Setear las RedisOptions
        RedisOptions redisOptions = new RedisOptions()
                .setConnectionString("redis://" + host + ":" + port)
                .setPassword(password);


        //Instanciar cliente reactivo de Redis
        Redis.createClient(vertx, redisOptions)
                .connect()
                .onSuccess(connection -> {
                    connection.send(Request.cmd(Command.PSUBSCRIBE).arg("*"))
                            .onSuccess(response -> System.out.println("Suscrito a eventos de Redis"))
                            .onFailure(error -> System.err.println("Error al suscribirse a eventos de Redis: " + error.getMessage()));


                    connection.handler(message -> {redisMessageHandler.handleMessage(message,connection);});


                })
                .onFailure(error ->{
                    System.out.println("No mi pana " + error.getMessage());
                });

        System.out.println("RedisMonitorVerticle start FIN");

    }




}
