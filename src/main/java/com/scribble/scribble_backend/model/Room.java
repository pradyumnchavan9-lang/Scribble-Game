package com.scribble.scribble_backend.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Data
public class Room {

    private String roomId;
    private List<Player> players = new ArrayList<>();
    private boolean gameStarted;
    private Player currentDrawer;
    private int drawerIndex;
    private String currentWord;
    private int roundNumber;
    private Map<String,Integer> playerScores;

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
