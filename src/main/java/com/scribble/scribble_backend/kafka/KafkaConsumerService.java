package com.scribble.scribble_backend.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scribble.scribble_backend.model.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumerService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "game-events", groupId = "scribble-group")
    public void consume(String jsonString) { // receive as String
        try {
            Message message = objectMapper.readValue(jsonString, Message.class); // parse JSON to Message
            String topic = "/topic/room/" + message.getRoomId();
            System.out.println("KAFKA BROADCASTING TO: " + topic);
            messagingTemplate.convertAndSend(topic, message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}