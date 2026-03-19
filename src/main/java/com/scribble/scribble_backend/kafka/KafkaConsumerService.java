package com.scribble.scribble_backend.kafka;


import com.scribble.scribble_backend.model.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumerService {

    @Autowired
    private SimpMessagingTemplate  messagingTemplate;

    //Listen to messages from a topic
    @KafkaListener(topics = "room-.*",groupId = "scribble-group")
    public void consume(Message message){
        String topic = "topic/room/" + message.getRoomId();
        messagingTemplate.convertAndSend(topic,message);
        // handle the message example broadcast to Web socket clients
    }
}
