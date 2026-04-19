    package com.scribble.scribble_backend.service;


    import com.scribble.scribble_backend.enums.MessageType;
    import com.scribble.scribble_backend.model.Message;
    import com.scribble.scribble_backend.model.Player;
    import com.scribble.scribble_backend.model.Room;
    import lombok.Data;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.context.event.EventListener;
    import org.springframework.messaging.simp.SimpMessagingTemplate;
    import org.springframework.stereotype.Service;
    import org.springframework.web.socket.messaging.SessionDisconnectEvent;

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

        private final Random random = new Random();

        private Map<String, ScheduledFuture<?>> roundTimers = new ConcurrentHashMap<>();

        private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);



        public void startGame(Message message){

            String roomId = message.getRoomId();
            Room room = roomManagerService.getRoom(roomId);

            if(room == null){
                throw new  RuntimeException("Room not found");
            }

            if(!room.getRoomOwner().equals(message.getSender())){
                return;
            }

            System.out.println("Room found: " + room);
            System.out.println("Players: " + room.getPlayers().size());
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

            if (room == null) {
                throw new NullPointerException("Room not found");
            }
            synchronized(room) {

                if(!room.isGameStarted()) {
                    return;
                }
                if(room.isRoundActive()){
                    return;
                }

                List<Player> players = room.getPlayers();
                for (Player p : players) {
                    p.setCorrectGuess(false);

                }


                //Select player using drawer Index
               Set<String> hasDrawn = room.getHasDrawn();
               for(Player p : players){
                   if(!hasDrawn.contains(p.getUsername())){
                       hasDrawn.add(p.getUsername());
                       room.setCurrentDrawer(p);
                       break;
                   }
               }


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
            String topic = "/topic/room/" + roomId;
            messagingTemplate.convertAndSend(topic, publicMsg);

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

                if(!room.isGameStarted()){
                    return;
                }
                if (!room.isRoundActive()) {
                    return;
                }

                room.setRoundActive(false);
                room.setHasGuessed(new HashSet<>());

                for (Player p : room.getPlayers()) {
                    if (p.isCorrectGuess()) {
                        foundAns = true;
                        break;
                    }
                }

                //Update Drawer index
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
            if(room.getHasDrawn().size() >= room.getPlayers().size()){
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
                }
                if(topPlayers.size() > 1){
                    publicMsg.setWinner("Tie between: " + String.join(", ",topPlayers));
                }else{
                    publicMsg.setWinner(topPlayers.get(0));
                }

            }
            String topic = "/topic/room/" + roomId;
            messagingTemplate.convertAndSend(topic, publicMsg);

            if(room.getHasDrawn().size() >= room.getPlayers().size()){
                room.setHasDrawn(new HashSet<>());
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

            if(room.getCurrentDrawer().getUsername().equals(message.getSender())){
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

                    if (guesser != null && drawer != null) {

                        //modify the scores of drawer and guesser
                        double drawerScore = ((double) 1 /players.size()) * 100;
                        double guesserScore = (players.size() - (double) room.getHasGuessed().size() )/(players.size()) * 100;
                        drawer.setScore(drawer.getScore() + (int)drawerScore);
                        guesser.setScore(guesser.getScore() + (int)guesserScore);

                        playerScores.put(guesser.getUsername(), guesser.getScore());
                        playerScores.put(drawer.getUsername(), drawer.getScore());

                        room.setPlayerScores(playerScores);

                        //add the player to the has guessed set
                        Set<String> haveGuessed = room.getHasGuessed();
                        haveGuessed.add(guesser.getUsername());

                        broadcast.setType(MessageType.CORRECT_GUESS);
                        broadcast.setPlayerScores(playerScores);
                        broadcast.setContent(message.getSender() + " guessed correctly!");

                    }
                }
                roomManagerService.saveRoom(room);

                String topic = "/topic/room/" + room.getRoomId();
                messagingTemplate.convertAndSend(topic, broadcast);

                if(room.getHasGuessed().size() >= room.getPlayers().size() - 1) {
                    //cancel the timer
                    ScheduledFuture<?> future = roundTimers.get(room.getRoomId());
                    future.cancel(false);
                    roundTimers.remove(room.getRoomId());
                    endRound(room.getRoomId());
                }

                return;
            }

            // wrong guess
            broadcast.setType(MessageType.CHAT);
            broadcast.setContent(guess);
            String topic = "/topic/room/" + room.getRoomId();
            messagingTemplate.convertAndSend(topic, broadcast);
        }


        @EventListener
        public void handleDisconnect(SessionDisconnectEvent event){
            String roomId = roomManagerService.getRoomIdFromSession(event.getSessionId());
            String username = event.getUser().getName();


            Room room = roomManagerService.getRoom(roomId);
            if(room == null){
                return;
            }

            //remove player from room
            List<Player> players = room.getPlayers();
            Iterator<Player> it = players.iterator();
            Set<String> haveDrawn = room.getHasDrawn();
            Set<String> hasGuessed = room.getHasGuessed();

            while(it.hasNext()){
                if(it.next().getUsername().equals(username)){
                    it.remove();
                }

            }

            //if leaver was the owner
            if(username.equals(room.getRoomOwner())) {
                room.setRoomOwner(players.get(0).getUsername());
                Message ownerLeft = new Message();
                String topic = "/topic/room/" + room.getRoomId();
                String content = username + " owner left the room! " + room.getRoomOwner() + " is the new room owner" ;
                ownerLeft.setContent(content);
                ownerLeft.setType(MessageType.ROOM_OWNER_CHANGED);
                ownerLeft.setRoomId(room.getRoomId());
                messagingTemplate.convertAndSend(topic, ownerLeft);
            }

            //remove player if he has drawn
            if(haveDrawn.contains(username)){
                haveDrawn.remove(username);
            }

            //remove player if he has guessed
            if(hasGuessed.contains(username)){
                hasGuessed.remove(username);
            }

            //remove session ID from map
            roomManagerService.removeSession(event.getSessionId());

            // if room empty remove room entirely
            if(players.isEmpty()){
                roomManagerService.removeRoom(roomId);
                return;
            }

            //Broadcast player left
            String content = "Player: " + username + " has left the room";
            Message broadcast = new Message();
            broadcast.setRoomId(room.getRoomId());
            broadcast.setSender(username);
            broadcast.setContent(content);
            String topic = "/topic/room/" + room.getRoomId();
            messagingTemplate.convertAndSend(topic, broadcast);

            //return if game not started
            if(!room.isGameStarted()){
                return;
            }

            //if room has less than 2 players
            if(players.size() < 2){

                ScheduledFuture<?> future = roundTimers.get(roomId);
                if(future != null){
                    future.cancel(true);
                    roundTimers.remove(roomId);
                }
                room.setGameStarted(false);
                String msg = "Game cannot be started with less than 2 players";
                Message message = new Message();
                message.setRoomId(room.getRoomId());
                message.setSender(username);
                message.setContent(msg);
                String topicName = "/topic/room/" + room.getRoomId();
                messagingTemplate.convertAndSend(topicName, message);
                return;
            }

            //if drawer left cancel timer or if all players except the left one have guessed
            if(room.getCurrentDrawer() != null &&
                    (room.getCurrentDrawer().getUsername().equals(username) || hasGuessed.size() >= players.size() - 1)){
                Map<String, ScheduledFuture<?>> futures = roundTimers;
                ScheduledFuture<?> future = futures.get(roomId);
                if(future != null){
                    future.cancel(true);
                    futures.remove(roomId);
                }
                roomManagerService.saveRoom(room);
                endRound(roomId);
            }

        }
    }
