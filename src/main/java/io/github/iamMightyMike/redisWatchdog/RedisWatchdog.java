package io.github.iamMightyMike.redisWatchdog;

import io.github.iamMightyMike.redisWatchdog.utils.PropertiesLoader;
import io.github.iamMightyMike.redisWatchdog.verticles.RedisMonitorVerticle;
import io.github.iamMightyMike.redisWatchdog.verticles.WebServerVerticle;
import io.vertx.core.Vertx;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class RedisWatchdog {

    public static void main(String[] args) {

        // Create Vertx instance
        Vertx vertx = Vertx.vertx();

        RedisMonitorVerticle redisMonitorVerticle = new RedisMonitorVerticle();
        redisMonitorVerticle.setRedisData(loadRedisData());


        // Deploy the Redis monitoring verticle
        vertx.deployVerticle(redisMonitorVerticle, ar -> {
            if (ar.succeeded()) {
                System.out.println("RedisMonitorVerticle deployed successfully");
            } else {
                System.out.println("Failed to deploy RedisMonitorVerticle: " + ar.cause().getMessage());
            }
        });

        WebServerVerticle webServerVerticle = new WebServerVerticle();

        vertx.deployVerticle(webServerVerticle, res ->{

            if (res.succeeded()) {
                System.out.println("WebServerVerticle deployed successfully");
            } else {
                System.out.println("Failed to deploy WebServerVerticle: " + res.cause().getMessage());
            }

        });
    }


    private static HashMap<String, Object> loadRedisData(){

        HashMap<String, Object> redisData = new HashMap<>();

        Properties properties = PropertiesLoader.load("application.properties");

        String host = properties.getProperty("redis.host", "127.0.0.1");
        int port = Integer.parseInt(properties.getProperty("redis.port", "6379"));
        String password = properties.getProperty("redis.password", null);

        Jedis jedis = new Jedis(host, port);

        // Authenticate if the password is provided
        if (password != null && !password.isEmpty()) {
            jedis.auth(password);
        }

        String cursor = "0";
        ScanParams scanParams = new ScanParams().match("*").count(100);

        do {
            // SCAN operation
            ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
            cursor = scanResult.getCursor();
            for (String key : scanResult.getResult()) {
                // Get the type of the key
                String keyType = jedis.type(key);

                //Strategy pattern here?
                switch (keyType) {
                    case "string" -> {
                        String value = jedis.get(key);
                        redisData.put(key, value);
                    }
                    case "hash" -> {
                        Map<String, String> hashValue = jedis.hgetAll(key);
                        redisData.put(key, hashValue);
                    }
                    case "list" -> {
                        // Assuming list fetch
                        java.util.List<String> listValue = jedis.lrange(key, 0, -1);
                        redisData.put(key, listValue);
                    }
                    case "set" -> {
                        java.util.Set<String> setValue = jedis.smembers(key);
                        redisData.put(key, setValue);
                    }
                    default -> System.err.println("Unsupported key type: " + keyType);
                }
            }
        } while (!"0".equals(cursor));


        return redisData;
    }

}
