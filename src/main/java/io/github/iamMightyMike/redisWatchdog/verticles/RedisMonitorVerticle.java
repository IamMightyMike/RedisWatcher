package io.github.iamMightyMike.redisWatchdog.verticles;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    EventBus eventBus;

    @Override
    public void start() {

        System.out.println("RedisMonitorVerticle start");



        eventBus = vertx.eventBus();

        // Set Redis options
        Redis.createClient(vertx, new RedisOptions())
                .connect()
                .onSuccess(connection -> {
                    System.out.println("Conectadiiisimo");

                    connection.send(Request.cmd(Command.PSUBSCRIBE).arg("*"))
                            .onSuccess(response -> System.out.println("Subscribed to Redis key events"))
                            .onFailure(error -> System.err.println("Failed to subscribe: " + error.getMessage()));


                    connection.handler(message -> {handleMessage(message,connection);});


                })
                .onFailure(error ->{
                    System.out.println("No mi pana " + error.getMessage());
                });

        System.out.println("RedisMonitorVerticle start FIN");

    }

    private void handleMessage(Response message,RedisConnection connection){

        //System.out.println("HandleMessage -------> " + message);
        if(message.type() == ResponseType.PUSH && message.stream().collect(Collectors.toList()).size() > 2) {
            if ("__keyevent@0__:hset".equals(message.get(2).toString())) {
                String daKey = message.get(3).toString();
                System.out.println("HSET -> " + message.get(3).toString());

                connection.send(Request.cmd(Command.HGETALL).arg(daKey))
                        .onSuccess(response -> {
                            if(redisData.containsKey(daKey)){
                                //System.out.println("ANTES -> " + redisData.get(daKey)+ " - NOW -> " + response);

                                //La response puede traer N fields con sus valores. Hacer un método para actualizar el HashMap
                                //Hacer un método para encontrar lo que ha cambiado en el hashmap. Tenemos la antigua key, con N field:value y la nueva key con N field:value. Hay que encontrar la diferencia y usar tan solo eso.

                                JsonObject moddedKeyObject = findModifiedOrDeletedField(response,daKey);
                                updateRedisData(response,daKey);

                                eventBus.publish("redis.data.update", moddedKeyObject);
                            }
                            else {

                                //System.out.println("NOW -> " + response);
                                String field = response.getKeys().stream().findFirst().orElse("");
                                String value = response.get(field).toString();

                                Object valuesForKeyObject =  redisData.get(daKey);

                                //if(valuesForKeyObject != null && valuesForKeyObject instanceof Map){
//
                                //    HashMap<String,String> valuesForKey = (HashMap<String,String>)valuesForKeyObject;
                                //    valuesForKey.put(field,value);
//
                                //}else {
                                //    redisData.put(daKey, Map.of(field, value));
                                //}

                                HashMap<String,String> newKey = new HashMap<>();
                                newKey.put(field, value);
                                redisData.put(daKey, newKey);
                                //redisData.put(daKey, Map.of(field, value));

                                JsonObject newValueObject = new JsonObject()
                                        .put(field,value);

                                JsonObject keyObject = new JsonObject()
                                        .put("key", daKey)
                                        .put("newField", newValueObject);

                                eventBus.publish("redis.data.update", keyObject);
                            }
                        });

            } else if ("__keyevent@0__:hdel".equals(message.get(2).toString())) {
                System.out.println("DEL -> " + message.get(3).toString());
                String daKey = message.get(3).toString();
                connection.send(Request.cmd(Command.HGETALL).arg(daKey))
                        .onSuccess(response -> {

                            JsonObject deletedKeyObject = findModifiedOrDeletedField(response,daKey);
                            JsonObject deletedField = deletedKeyObject.getJsonObject("oldField");
                            String deletedFieldName = deletedField.stream().map(entry -> entry.getKey()).findFirst().orElse(null);

                            if(deletedFieldName != null){

                                Object valuesForKeyObject =  redisData.get(daKey);

                                if(valuesForKeyObject != null && valuesForKeyObject instanceof Map){
//
                                    HashMap<String,String> valuesForKey = (HashMap<String,String>)valuesForKeyObject;
                                    valuesForKey.remove(deletedFieldName);
                                    redisData.put(daKey,valuesForKey);
                                }

                            }
                            eventBus.publish("redis.data.update", deletedKeyObject);
                        });

            }else if ("__keyevent@0__:set".equals(message.get(2).toString())) {
                System.out.println("SET -> " + message.get(3).toString());
            }
        }

    }

    public void setRedisData(HashMap<String, Object> redisData) {
        this.redisData = redisData;
    }


    private void updateRedisData(Response hsetResponse, String key){

        Map<String, String> newKeyValue = new HashMap<>();

        hsetResponse.getKeys().stream().forEach(aField -> {
                String value = hsetResponse.get(aField).toString();
                newKeyValue.put(aField,value);
            });

        redisData.put(key,newKeyValue);
    }


    private JsonObject findModifiedOrDeletedField(Response hsetResponse, String key){

        Object keyValue = redisData.get(key);

        if(keyValue instanceof Map){
            Map<String,String> keyPrevValue = (Map<String, String>)keyValue;

            int responseSize = hsetResponse.getKeys().size();
            int storedKeySize = keyPrevValue.size();

            Set<String> responseFields = hsetResponse.getKeys();
            Set<String> redisDataFields = keyPrevValue.keySet();

            //System.out.println("Keys HGETALL " +hsetResponse.getKeys().stream().collect(Collectors.joining(",")));
            //System.out.println("Keys RedisData " +((HashMap<String,String>)redisData.get(key)).keySet().stream().collect(Collectors.joining(",")));

            if(responseSize == storedKeySize){
                //Field modificado

                String modifiedField = responseFields.stream().filter(aField -> {
                    String aResponseValue = hsetResponse.get(aField).toString();
                    String redisDataValue = keyPrevValue.get(aField);

                    return !aResponseValue.equals(redisDataValue);

                }).findFirst().orElse("");

                if(!modifiedField.isEmpty()){

                    String modifiedValue = hsetResponse.get(modifiedField).toString();
                    JsonObject newValueObject = new JsonObject()
                            .put(modifiedField,modifiedValue);

                    JsonObject oldValueObject = new JsonObject()
                            .put(modifiedField,keyPrevValue.get(modifiedField));

                    JsonObject keyObject = new JsonObject()
                            .put("key", key)
                            .put("oldField", oldValueObject)
                            .put("newField", newValueObject);

                    return keyObject;
                }


            }
            else{

                //TODO Ojo, considerar que

                JsonObject keyObject = new JsonObject();

                if(responseSize > storedKeySize) {
                    //Field añadido
                    Set<String> responseFieldsCopy = new HashSet<>(responseFields);
                    responseFieldsCopy.removeAll(redisDataFields);

                    String addedField = responseFieldsCopy.stream().findFirst().orElse("");
                    String addedValue = hsetResponse.get(addedField).toString();

                    JsonObject newValueObject = new JsonObject()
                            .put(addedField,addedValue);

                     keyObject.put("key", key)
                            .put("newField", newValueObject);

                }else{
                    Set<String> redisDataFieldsCopy = new HashSet<>(redisDataFields);
                    redisDataFieldsCopy.removeAll(responseFields);

                    String removedField = redisDataFieldsCopy.stream().findFirst().orElse("");
                    String removedValue = keyPrevValue.get(removedField).toString();

                    JsonObject deletedValueObject = new JsonObject()
                            .put(removedField,removedValue);

                    keyObject.put("key", key)
                            .put("oldField", deletedValueObject);
                }



                return keyObject;
            }



        }
        else{
            return null;
            //Lo que hay guardado en Redis Data no es un map. No se puede hacer nada
        }

        return null;


    }

}
