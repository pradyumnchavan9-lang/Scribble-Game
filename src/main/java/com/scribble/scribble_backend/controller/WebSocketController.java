package com.scribble.scribble_backend.controller;


import com.scribble.scribble_backend.enums.MessageType;
import com.scribble.scribble_backend.model.Message;
import com.scribble.scribble_backend.model.Player;
import com.scribble.scribble_backend.model.Room;
import com.scribble.scribble_backend.service.RoomManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketController {

    @Autowired
    private RoomManager roomManager;

    @MessageMapping("/chat")
    @SendTo("/topic/messages")
    public Message handleChat(Message message){
        return message;
    }


    @MessageMapping("/room")
    @SendTo("/topic/room")
    public Message joinRoom(Message message){
        Room room  = roomManager.getRoom(message.getRoomId());
        if(room == null){
            room = roomManager.createRoom(message.getRoomId());
        }

        Player player = new Player(message.getSender(), message.getSender());
        room.addPlayer(player);

        Message broadcast = new Message();
        broadcast.setType(MessageType.PLAYER_JOINED);
        broadcast.setRoomId(room.getRoomId());
        broadcast.setSender(player.getUsername());
        broadcast.setContent(player.getUsername() + " joined the room");

        return broadcast;
    }

}
