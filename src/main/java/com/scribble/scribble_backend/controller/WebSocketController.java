package com.scribble.scribble_backend.controller;


import com.scribble.scribble_backend.enums.MessageType;
import com.scribble.scribble_backend.model.Message;
import com.scribble.scribble_backend.model.Player;
import com.scribble.scribble_backend.model.Room;
import com.scribble.scribble_backend.service.GameEngine;
import com.scribble.scribble_backend.service.RoomManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;

@Controller
public class WebSocketController {

    @Autowired
    private RoomManager roomManager;
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
    public void joinRoom(Message message){


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
        roomManager.saveRoom(room);
        String topic = "/topic/room/" + room.getRoomId();
        messagingTemplate.convertAndSend(topic, broadcast);
    }

    //Draw Event
    @MessageMapping("/draw")
    public void handleDraw(Message message){

        String roomTopic = "/topic/room/" + message.getRoomId();
        messagingTemplate.convertAndSend(roomTopic,message);
    }

    //Validate Guess
    @MessageMapping("/guess")
    public void validateGuess(Message message){
        String guess = message.getContent();
        Room room = roomManager.getRoom(message.getRoomId());
        if(room == null){
            throw new RuntimeException("room not found");
        }
        String correctAns = room.getCurrentWord();

        Message broadcast = new Message();
        broadcast.setRoomId(message.getRoomId());
        broadcast.setSender(message.getSender());
        List<Player> players = room.getPlayers();
        Map<String,Integer> playerScores = room.getPlayerScores();
        if(guess.equalsIgnoreCase(correctAns)){
            broadcast.setType(MessageType.CORRECT_GUESS);
            for(Player player : players){

                //Increment correct guesser's score
                if(player.getUsername().equals(message.getSender()) && !player.isCorrectGuess()){

                    //Increment Drawers Score
                    Player drawer = room.getCurrentDrawer();
                    drawer.setScore(drawer.getScore() + 5);
                    player.setCorrectGuess(true);
                    player.setScore(player.getScore() + 10);

                    //Update player scores
                    playerScores.put(player.getUsername(),player.getScore());
                    playerScores.put(drawer.getUsername(),drawer.getScore());
                    room.setPlayerScores(playerScores);
                    broadcast.setPlayerScores(playerScores);
                    roomManager.saveRoom(room);
                    gameEngine.endRound(room.getRoomId());
                }

            }
            String content = message.getSender() + " guessed correctly!";
            broadcast.setContent(content);
        }else{
            broadcast.setType(MessageType.CHAT);
            broadcast.setContent(guess);
        }
        roomManager.saveRoom(room);
        String roomTopic = "/topic/room/" + message.getRoomId();
        messagingTemplate.convertAndSend(roomTopic,broadcast);
    }

    //Start Game
    @MessageMapping("/start")
    public void startGame(Message message){

        gameEngine.startGame(message.getRoomId());
    }
}
