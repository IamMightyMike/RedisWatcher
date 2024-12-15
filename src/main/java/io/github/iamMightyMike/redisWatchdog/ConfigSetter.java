package io.github.iamMightyMike.redisWatchdog;

import redis.clients.jedis.Jedis;

public class ConfigSetter {


    public static void main(String[] args) {

        //String redisHost = "localhost"; // Replace with your Redis host
        //int redisPort = 6379;

        String redisHost = "f21-qa-redis-master.es.gestamp.com"; // Replace with your Redis host
        int redisPort = 80;
        String password = "REzQHC3R91YZVMK7DAa9QgzGFVSixfceugEH";
        //REzQHC3R91YZVMK7DAa9QgzGFVSixfceugEH

        Jedis jedis = new Jedis(redisHost, redisPort);
        jedis.auth(password);

        //jedis.configSet("notify-keyspace-events", "AKE");

        jedis.configGet("notify-keyspace-events").stream().forEach(System.out::println);
        //notify-keyspace-events

        //jedis.configGet("*").stream().forEach(System.out::println);


    }
}
