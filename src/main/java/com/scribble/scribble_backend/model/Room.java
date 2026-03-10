package com.scribble.scribble_backend.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;


@Data
public class Room {

    private String roomId;
    private List<Player> players = new ArrayList<>();
    private boolean gameStarted;

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
