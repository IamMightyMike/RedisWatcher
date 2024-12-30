package io.github.iamMightyMike.redisWatchdog.handler;

import io.github.iamMightyMike.redisWatchdog.model.RedisEvent;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.client.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RedisMessageHandler {
    private HashMap<String, Object> redisData;

    private final EventBus eventBus;

    public RedisMessageHandler( EventBus eventBus,HashMap<String, Object> redisData) {
        this.redisData = redisData;
        this.eventBus = eventBus;
    }

    public void handleMessage(Response message, RedisConnection connection) {
        // Según tipo de evento, invoacr un handler u otro
        String eventType = message.get(2).toString();
        switch (eventType) {
            case RedisEvent.HSET:
                handleHSet(message,connection);
                break;
            case RedisEvent.HDEL:
                handleHDel(message,connection);
                break;
            // TODO - considerar otros tipos de eventos para otros tipos de clave en Redis
        }
    }

    private void handleHSet(Response message,RedisConnection connection){
        String daKey = message.get(3).toString();
        System.out.println("HSET -> " + message.get(3).toString());

        connection.send(Request.cmd(Command.HGETALL).arg(daKey))
                .onSuccess(response -> {
                    if(redisData.containsKey(daKey)){

                        JsonObject moddedKeyObject = findModifiedOrDeletedField(response,daKey);
                        updateRedisData(response,daKey);

                        eventBus.publish("redis.data.update", moddedKeyObject);
                    }
                    else {

                        //System.out.println("NOW -> " + response);
                        String field = response.getKeys().stream().findFirst().orElse("");
                        String value = response.get(field).toString();

                        Object valuesForKeyObject =  redisData.get(daKey);

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

    }

    private void handleHDel(Response message,RedisConnection connection){

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
