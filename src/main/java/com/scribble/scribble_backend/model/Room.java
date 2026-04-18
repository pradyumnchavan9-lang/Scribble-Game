package com.scribble.scribble_backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;


@Data
@NoArgsConstructor
public class Room {

    private String roomId;
    private List<Player> players = new ArrayList<>();
    private boolean gameStarted;
    private Player currentDrawer;
    private Set<String> hasDrawn = new HashSet<>();
    private String currentWord;
    private Map<String,Integer> playerScores;
    private boolean roundActive;

    public Room(String roomId){
        this.roomId = roomId;
    }

    public void addPlayer(Player player){
        this.players.add(player);
    }

    public void removePlayer(Player player){
        this.players.remove(player);
    }


}
