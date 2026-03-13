package com.scribble.scribble_backend.service;


import com.scribble.scribble_backend.model.Room;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class RoomManager {

    @Autowired
    private RedisService redisService;

    public Room createRoom(String roomId) {
        Room room = new Room(roomId);

        String key = "room:" + roomId;
        redisService.set(key,room,300L);
        return room;
    }

    public Room getRoom(String roomId){
        String key =  "room:" + roomId;
        return redisService.get(key,Room.class);
    }

    public void saveRoom(Room room){
        String key = "room:" + room.getRoomId();
        redisService.set(key,room,300L);
    }

}
