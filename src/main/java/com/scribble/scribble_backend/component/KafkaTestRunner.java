//package com.scribble.scribble_backend.component;
//
//import com.scribble.scribble_backend.kafka.KafkaProducerService;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.stereotype.Component;
//
//@Component
//public class KafkaTestRunner implements CommandLineRunner {
//
//
//    @Autowired
//    private KafkaProducerService producer;
//
//    @Override
//    public void run(String... args) throws Exception {
//        System.out.println("Sending test Kafka message...");
//        producer.sendMessage("room-room123", "Hello Kafka!");
//    }
//}
