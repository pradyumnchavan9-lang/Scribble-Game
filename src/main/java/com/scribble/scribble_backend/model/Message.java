package com.scribble.scribble_backend.model;


import com.scribble.scribble_backend.enums.MessageType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private String color;
    private Double thickness;

}
