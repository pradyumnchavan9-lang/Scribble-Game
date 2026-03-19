package com.scribble.scribble_backend.model;


import com.scribble.scribble_backend.enums.MessageType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    private MessageType type;
    private String sender;
    private String content;
    private String roomId;

    //for draw event we need
    private Integer x;
    private Integer y;
    private Integer prevX;
    private Integer prevY;
    private String color;
    private Double thickness;

    private String drawer;
    private Map<String,Integer> playerScores;
    private String winner;

}
