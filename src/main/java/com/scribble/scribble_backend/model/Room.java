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
    private Set<String> hasGuessed = new HashSet<>();
    private String currentWord;
    private Map<String,Integer> playerScores;
    private boolean roundActive;
    private String roomOwner;

    public Room(String roomId, String roomOwner){

        this.roomId = roomId;
        this.roomOwner = roomOwner;
    }

    public void addPlayer(Player player){
        this.players.add(player);
    }

    public void removePlayer(Player player){
        this.players.remove(player);
    }

    public void addGuessed(String guess){
        this.hasGuessed.add(guess);
    }
}
