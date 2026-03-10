package com.scribble.scribble_backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Player {

    private String playerId;
    private String username;
}
