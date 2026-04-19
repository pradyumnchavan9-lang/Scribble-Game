package com.scribble.scribble_backend.service;


import com.scribble.scribble_backend.enums.MessageType;
import com.scribble.scribble_backend.model.Message;
import com.scribble.scribble_backend.model.Player;
import com.scribble.scribble_backend.model.Room;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Component
@Slf4j
public class RoomManagerService {


    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    private Map<String, Room> rooms ;
    private Map<String, String> sessionToRoom;

    public RoomManagerService() {
        this.rooms = new ConcurrentHashMap<>();
        this.sessionToRoom = new ConcurrentHashMap<>();
    }

    public Room getRoom(String roomId){
        return rooms.get(roomId);
    }

    public void saveRoom(Room room){
        String roomId = room.getRoomId();
        rooms.put(roomId, room);
    }

    //Join Room
    public void joinRoom(Message message, SimpMessageHeaderAccessor headerAccessor){

        //  make sure the room exists, putIfAbsent is already synchronized
        rooms.putIfAbsent(message.getRoomId(), new Room(message.getRoomId(), message.getSender()));

        // Get the room
        Room room  = getRoom(message.getRoomId());

        // initialize the message
        Message broadcast = new Message();

        // make sure only one thread can access the room
        synchronized(room) {

            // create the player who's joining the room
            Player player = new Player(message.getSender(), message.getSender());
            room.addPlayer(player);

            broadcast.setType(MessageType.PLAYER_JOINED);
            broadcast.setRoomId(room.getRoomId());
            broadcast.setSender(player.getUsername());
            broadcast.setContent(player.getUsername() + " joined the room");

            //save room with the player
            sessionToRoom.put(headerAccessor.getSessionId(), room.getRoomId());
            saveRoom(room);
        }
        String topic = "/topic/room/" + room.getRoomId();
        messagingTemplate.convertAndSend(topic, broadcast);
    }


    //Handle Draw Event
    public void handleDraw(Message message){

        String sender = message.getSender();
        String roomId = message.getRoomId();

        Room room = getRoom(roomId);

        if(room == null || room.getCurrentDrawer() == null){
            return; // game not ready
        }

        String actualDrawer = room.getCurrentDrawer().getUsername();
        if(!actualDrawer.equals(sender)){
            log.warn("Unauthorized draw attempt: {} in room {}", sender, roomId);
            return;
        }
        String roomTopic = "/topic/room/" + message.getRoomId();
        messagingTemplate.convertAndSend(roomTopic,message);
    }

    //get roomId from session
    public String getRoomIdFromSession(String sessionId){
        return sessionToRoom.get(sessionId);
    }

    //remove sessionId once the user disconnects
    public void removeSession(String sessionId){
        sessionToRoom.remove(sessionId);
    }

    //remove room from map once its empty
    public void removeRoom(String roomId){
        rooms.remove(roomId);
    }

}
