package com.scribble.scribble_backend.service;


import com.scribble.scribble_backend.enums.MessageType;
import com.scribble.scribble_backend.model.Message;
import com.scribble.scribble_backend.model.Player;
import com.scribble.scribble_backend.model.Room;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;


@Component
@Slf4j
public class RoomManagerService {

    @Autowired
    private RedisService redisService;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;


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

    //Join Room
    public void joinRoom(Message message){


        Room room  = getRoom(message.getRoomId());
        if(room == null){
            room = createRoom(message.getRoomId());
        }

        Player player = new Player(message.getSender(), message.getSender());
        room.addPlayer(player);


        Message broadcast = new Message();
        broadcast.setType(MessageType.PLAYER_JOINED);
        broadcast.setRoomId(room.getRoomId());
        broadcast.setSender(player.getUsername());
        broadcast.setContent(player.getUsername() + " joined the room");
        saveRoom(room);
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

}
