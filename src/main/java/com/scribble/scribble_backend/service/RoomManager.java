package com.scribble.scribble_backend.service;


import com.scribble.scribble_backend.model.Room;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RoomManager {

    private Map<String, Room> rooms = new ConcurrentHashMap<>();
    private Map<String, String> roomWords = new ConcurrentHashMap<>();

    public Room createRoom(String roomId) {
        Room room = new Room(roomId);
        roomWords.putIfAbsent(roomId,"apple");
        rooms.put(roomId, room);
        return room;
    }

    public Room getRoom(String roomId){
        return rooms.get(roomId);
    }

    public String getCorrectWord(String roomId){
        return roomWords.get(roomId);
    }


    public void removeRoom(String roomId){
        rooms.remove(roomId);
    }

}
