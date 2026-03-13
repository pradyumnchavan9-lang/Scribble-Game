package com.scribble.scribble_backend.service;


import com.scribble.scribble_backend.enums.MessageType;
import com.scribble.scribble_backend.model.Message;
import com.scribble.scribble_backend.model.Player;
import com.scribble.scribble_backend.model.Room;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class GameEngine {

    List<String> words = new ArrayList<>(List.of(
            "cat","dog","tree","house","car","sun","moon","star","fish","apple",
            "book","chair","table","phone","clock","shoe","hat","key","cup","ball",
            "bicycle","airplane","guitar","camera","computer","backpack","umbrella",
            "rocket","castle","mountain","volcano","island","desert","ocean",
            "alien","robot","dragon","wizard","vampire","zombie","spaceship",
            "pirate","ninja","monster","dinosaur","unicorn","black hole"
    ));

    @Autowired
    private RoomManager roomManager;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final Random random = new Random();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

    public void startGame(String roomId){

        Room room = roomManager.getRoom(roomId);
        if(room == null){
            throw new  RuntimeException("Room not found");
        }
        if(room.isGameStarted()){
           throw new RuntimeException("Game already started");
        }
        if(room.getPlayers().size() < 2){
            throw new RuntimeException("Room has less than 2 players");
        }

        room.setGameStarted(true);
        room.setRoundNumber(0);
        room.setDrawerIndex(0);

        List<Player> players = room.getPlayers();
        Map<String,Integer> playerScores = new ConcurrentHashMap<>();
        for(Player p : players){
            p.setScore(0);
            playerScores.put(p.getUsername(),0);
        }
        room.setPlayerScores(playerScores);
        roomManager.saveRoom(room);
        startRound(roomId);
    }


    public void startRound(String roomId){


        //Get the room
        Room room  = roomManager.getRoom(roomId);
        if(room == null){
            throw new   RuntimeException("Room not found");
        }

        List<Player> players = room.getPlayers();
        for(Player p : players){
            p.setCorrectGuess(false);

        }

        //Increment Round number
        room.setRoundNumber(room.getRoundNumber() + 1);

        //Select player using drawer Index
        int index = room.getDrawerIndex() % room.getPlayers().size();
        room.setCurrentDrawer(room.getPlayers().get(index));

        //Select a random word to be guessed
        String correctWord = words.get(random.nextInt(words.size()));
        room.setCurrentWord(correctWord);
        room.setRoundActive(true);
        roomManager.saveRoom(room);

        //2 broadcast messages
        //1st to everyone
        Message publicMsg = new Message();
        publicMsg.setType(MessageType.ROUND_START);
        publicMsg.setRoomId(roomId);
        publicMsg.setDrawer(room.getCurrentDrawer().getUsername());
        publicMsg.setContent("");
        String topicRoom = "/topic/room/" + roomId;
        messagingTemplate.convertAndSend(topicRoom, publicMsg);

        //2nd message privately to the drawer
        Message privateMsg = new Message();
        privateMsg.setType(MessageType.ROUND_START);
        privateMsg.setRoomId(roomId);
        privateMsg.setDrawer(room.getCurrentDrawer().getUsername());
        privateMsg.setContent(correctWord);

        messagingTemplate.convertAndSendToUser(
                room.getCurrentDrawer().getUsername(),
                "/queue/private",
                privateMsg
        );

        //End round after 60 seconds
        int roundDurationSeconds = 10;
        scheduler.schedule(() -> endRound(roomId),roundDurationSeconds, TimeUnit.SECONDS);
    }

    public void endRound(String roomId){

        Room room =  roomManager.getRoom(roomId);

        if(room == null){
            throw new RuntimeException("Room not found");
        }
        if(!room.isRoundActive()){
            return;
        }

        room.setRoundActive(false);
        roomManager.saveRoom(room);
        boolean foundAns = false;
        for(Player p : room.getPlayers()){
            if(p.isCorrectGuess()){
                foundAns = true;
                break;
            }
        }

        Message publicMsg = new Message();
        publicMsg.setRoomId(roomId);
        publicMsg.setType(MessageType.ROUND_END);
        if(!foundAns){
            publicMsg.setContent(room.getCurrentWord());
        }

        //Get player scores
       Map<String,Integer> playerScores = room.getPlayerScores();

        //Update Drawer index
        room.setDrawerIndex(room.getDrawerIndex() + 1);
        //Save room
        roomManager.saveRoom(room);

        //Set player scores to message
        publicMsg.setPlayerScores(playerScores);

        //topic room for broadcasting
        String topicRoom = "/topic/room/" + roomId;

        //game end condition
        if(room.getRoundNumber() == room.getPlayers().size()){
            publicMsg.setContent("Game Ended with Scores");
            int max = Integer.MIN_VALUE;
            String winner = "";
            for(Map.Entry<String,Integer> entry : playerScores.entrySet()){
                if(entry.getValue() > max){
                    max = entry.getValue();
                    winner = entry.getKey();
                }
            }
            publicMsg.setWinner(winner);
        }
        messagingTemplate.convertAndSend(topicRoom, publicMsg);

        if(room.getRoundNumber() == room.getPlayers().size()){
            room.setRoundNumber(0);
            room.setDrawerIndex(0);
            room.setRoundActive(false);
            room.setGameStarted(false);
            roomManager.saveRoom(room);
            return;
        }
        startRound(roomId);
    }
}
