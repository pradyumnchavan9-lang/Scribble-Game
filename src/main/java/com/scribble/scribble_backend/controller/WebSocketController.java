package com.scribble.scribble_backend.controller;


import com.scribble.scribble_backend.model.Message;
import com.scribble.scribble_backend.service.GameEngine;
import com.scribble.scribble_backend.service.RoomManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;


@Controller
public class WebSocketController {

    @Autowired
    private RoomManagerService roomManagerService;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    @Autowired
    private GameEngine gameEngine;

    @MessageMapping("/chat")
    @SendTo("/topic/messages")
    public Message handleChat(Message message){
        return message;
    }


    //Join Room
    @MessageMapping("/room")
    public void joinRoom(Message message, SimpMessageHeaderAccessor headerAccessor){
        roomManagerService.joinRoom(message, headerAccessor);
    }

    //Draw Event
    @MessageMapping("/draw")
    public void handleDraw(Message message){
        roomManagerService.handleDraw(message);
    }

    //Validate Guess
    @MessageMapping("/guess")
    public void validateGuess(Message message){
        gameEngine.validateGuess(message);
    }

    //Start Game
    @MessageMapping("/start")
    public void startGame(Message message){
        gameEngine.startGame(message.getRoomId());
    }
}
