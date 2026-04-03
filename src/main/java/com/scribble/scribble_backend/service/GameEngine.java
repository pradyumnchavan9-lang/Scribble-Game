package com.scribble.scribble_backend.service;


import com.scribble.scribble_backend.enums.MessageType;
import com.scribble.scribble_backend.kafka.KafkaProducerService;
import com.scribble.scribble_backend.model.Message;
import com.scribble.scribble_backend.model.Player;
import com.scribble.scribble_backend.model.Room;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

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
    private RoomManagerService roomManagerService;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    @Autowired
    private KafkaProducerService kafkaProducerService;

    private final Random random = new Random();

    private Map<String, ScheduledFuture<?>> roundTimers = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

    public void startGame(String roomId){
        System.out.println("startGame called for room: " + roomId);

        Room room = roomManagerService.getRoom(roomId);
        System.out.println("Room found: " + room);
        System.out.println("Players: " + room.getPlayers().size());
        if(room == null){
            throw new  RuntimeException("Room not found");
        }
        // Locked
        synchronized(room) {
            if (room.isGameStarted()) {
                System.out.println("Game already started, returning");
                return;
            }
            if (room.getPlayers().size() < 2) {
                System.out.println("Not enough players: " + room.getPlayers().size());
                throw new RuntimeException("Room has less than 2 players");
            }

            room.setGameStarted(true);

            room.setRoundNumber(0);
            room.setDrawerIndex(0);

            List<Player> players = room.getPlayers();
            Map<String, Integer> playerScores = new ConcurrentHashMap<>();
            for (Player p : players) {
                p.setScore(0);
                playerScores.put(p.getUsername(), 0);
            }
            room.setPlayerScores(playerScores);
        }
        roomManagerService.saveRoom(room);
        startRound(roomId);
    }


    public void startRound(String roomId){


        //Get the room
        Room room  = roomManagerService.getRoom(roomId);
        synchronized(room) {

            if(room.isRoundActive()){
                return;
            }
            if (room == null) {
                throw new RuntimeException("Room not found");
            }

            List<Player> players = room.getPlayers();
            for (Player p : players) {
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
        }
        roomManagerService.saveRoom(room);

        //2 broadcast messages
        //1st to everyone
        Message publicMsg = new Message();
        publicMsg.setType(MessageType.ROUND_START);
        publicMsg.setRoomId(roomId);
        publicMsg.setDrawer(room.getCurrentDrawer().getUsername());
        publicMsg.setContent("");
        kafkaProducerService.sendMessage(publicMsg);

        //2nd message privately to the drawer
        Message privateMsg = new Message();
        privateMsg.setType(MessageType.ROUND_START);
        privateMsg.setRoomId(roomId);
        privateMsg.setDrawer(room.getCurrentDrawer().getUsername());
        String correctWord = room.getCurrentWord();
        privateMsg.setContent(correctWord);

        messagingTemplate.convertAndSendToUser(
                room.getCurrentDrawer().getUsername(),
                "/queue/private",
                privateMsg
        );

        //End round after 60 seconds
        int roundDurationSeconds = 60;
        ScheduledFuture<?> future = scheduler.schedule(() -> endRound(room.getRoomId()),roundDurationSeconds, TimeUnit.SECONDS);
        //Put the remote in the map
        roundTimers.put(roomId,future);
    }


    //End Round
    public void endRound(String roomId){

        Room room = roomManagerService.getRoom(roomId);
        if (room == null) {
            throw new RuntimeException("Room not found");
        }
        boolean foundAns = false;
        Map<String, Integer> scoresSnapshot;
        String currentWordSnapshot;
        synchronized(room) {

            if (!room.isRoundActive()) {
                return;
            }

            room.setRoundActive(false);
            for (Player p : room.getPlayers()) {
                if (p.isCorrectGuess()) {
                    foundAns = true;
                    break;
                }
            }

            //Update Drawer index
            room.setDrawerIndex(room.getDrawerIndex() + 1);
            scoresSnapshot = new HashMap<>(room.getPlayerScores());
            currentWordSnapshot = room.getCurrentWord();
        }

        Message publicMsg = new Message();
        publicMsg.setRoomId(room.getRoomId());
        publicMsg.setType(MessageType.ROUND_END);
        if(!foundAns){
            publicMsg.setContent(currentWordSnapshot);
        }

        //Get player scores
       Map<String,Integer> playerScores = scoresSnapshot;

        //Save room
        roomManagerService.saveRoom(room);

        //Set player scores to message
        publicMsg.setPlayerScores(playerScores);


        //game end condition
        if(room.getRoundNumber() >= room.getPlayers().size()){
            publicMsg.setContent("Game Ended with Scores");
            int max = Integer.MIN_VALUE;
            List<String> topPlayers = new ArrayList<>();
            for(Map.Entry<String,Integer> entry : playerScores.entrySet()){
                if(entry.getValue() > max){
                    max = entry.getValue();
                    topPlayers.clear();
                    topPlayers.add(entry.getKey());
                }else{
                    if(max == entry.getValue()){
                        topPlayers.add(entry.getKey());
                    }
                }

                if(topPlayers.size() > 1){
                    publicMsg.setWinner("Tie between: " + String.join(", ",topPlayers));
                }else{
                    publicMsg.setWinner(topPlayers.get(0));
                }
            }

        }
        kafkaProducerService.sendMessage(publicMsg);

        if(room.getRoundNumber() >= room.getPlayers().size()){
            room.setRoundNumber(0);
            room.setDrawerIndex(0);
            room.setRoundActive(false);
            room.setGameStarted(false);
            roomManagerService.saveRoom(room);
            return;
        }
        roundTimers.remove(room.getRoomId());
        startRound(room.getRoomId());
    }

    //Validate Guess Event
    public void validateGuess(Message message){

        Room room = roomManagerService.getRoom(message.getRoomId());
        if(room == null){
            throw new RuntimeException("room not found");
        }

        if(!room.isRoundActive()){
            return;
        }


        String guess = message.getContent();
        String correctAns = room.getCurrentWord();

        Message broadcast = new Message();
        broadcast.setRoomId(message.getRoomId());
        broadcast.setSender(message.getSender());

        List<Player> players = room.getPlayers();
        Map<String,Integer> playerScores = room.getPlayerScores();

        if(guess.trim().equalsIgnoreCase(correctAns)){

            // cancel timer
            ScheduledFuture<?> future = roundTimers.get(message.getRoomId());
            if(future != null){
                future.cancel(false);
                roundTimers.remove(message.getRoomId());
            }

            synchronized(room) {
                Player guesser = null;
                Player drawer = null;

                for (Player player : players) {
                    if (player.getUsername().equals(message.getSender()) && !player.isCorrectGuess()) {
                        guesser = player;
                        guesser.setCorrectGuess(true);
                    }
                    if (player.getUsername().equals(room.getCurrentDrawer().getUsername())) {
                        drawer = player;
                    }
                }

                if (guesser != null) {



                    drawer.setScore(drawer.getScore() + 5);
                    guesser.setScore(guesser.getScore() + 10);

                    playerScores.put(guesser.getUsername(), guesser.getScore());
                    playerScores.put(drawer.getUsername(), drawer.getScore());

                    room.setPlayerScores(playerScores);

                    broadcast.setType(MessageType.CORRECT_GUESS);
                    broadcast.setPlayerScores(playerScores);
                    broadcast.setContent(message.getSender() + " guessed correctly!");

                }
            }
            roomManagerService.saveRoom(room);
            kafkaProducerService.sendMessage(broadcast);
            endRound(room.getRoomId());

            return;
        }

        // wrong guess
        broadcast.setType(MessageType.CHAT);
        broadcast.setContent(guess);

        kafkaProducerService.sendMessage(broadcast);
    }
}
