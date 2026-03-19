package com.scribble.scribble_backend.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scribble.scribble_backend.model.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaProducerService {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate; // use String

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void sendMessage(Message message) {
        String topic = "room-" + message.getRoomId();
        try {
            String jsonString = objectMapper.writeValueAsString(message); // convert to JSON string
            kafkaTemplate.send(topic, jsonString);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }
}