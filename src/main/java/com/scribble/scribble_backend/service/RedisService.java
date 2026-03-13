package com.scribble.scribble_backend.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;

@Service
public class RedisService {

    private final RedisTemplate<String,String> redisTemplate;
    public RedisService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private final ObjectMapper mapper = new ObjectMapper();

    //Set key -> value
    public void set(String key,Object o,Long ttl){

        try{
            String jsonValue = mapper.writeValueAsString(o);
            redisTemplate.opsForValue().set(key,jsonValue,ttl, TimeUnit.SECONDS);
        }catch(Exception e){
            System.err.println("set key error:"+e.getMessage());
        }
    }

    //Get value using key
    public <T> T get(String key , Class<T> entityClass){
        try{
            Object o =  redisTemplate.opsForValue().get(key);
            if(o==null){
                return null;
            }
            return mapper.readValue(o.toString(),entityClass);
        }catch(Exception e){
            return null;
        }
    }
}
