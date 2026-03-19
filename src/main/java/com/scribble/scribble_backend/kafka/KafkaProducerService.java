package com.scribble.scribble_backend.kafka;


import com.scribble.scribble_backend.model.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaProducerService {

    @Autowired
    private KafkaTemplate<String, Message> kafkaTemplate;

    //Send message to a topic
    public void sendMessage(Message message){
        String topic = "room-" + message.getRoomId();
        kafkaTemplate.send(topic,message);
    }

    //

}
