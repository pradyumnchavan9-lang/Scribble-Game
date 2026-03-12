package com.scribble.scribble_backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Player {

    public Player(String playerId,String username){
        this.username = username;
        this.playerId = playerId;
    }

    private String playerId;
    private String username;
    private boolean correctGuess;
    private int score;
}
