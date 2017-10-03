///
// Copyright (c) 2016. Highfive Technologies, Inc.
///
package org.jitsi.videobridge.logging;

import net.java.sip.communicator.util.Logger;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

import javax.json.JsonObject;
import java.util.Properties;

public class KafkaAppender {
    private static final Logger logger
            = Logger.getLogger(KafkaAppender.class);
    private final Producer<String, String> producer;
    private final String kafkaTopic;

    public KafkaAppender(String kafkaTopic, String kafkaHost) {
        Properties props = new Properties();
        props.put("bootstrap.servers", kafkaHost);
        props.put("acks", "0");
        props.put("retries", 0);
        props.put("batch.size", 16384);
        props.put("linger.ms", 10000); // Send the batch every 5 seconds
        props.put("buffer.memory", 33554432);
        props.put("producer.type", "async");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        logger.info(String.format("Starting kafka producer: bootstrap-server:%s, topic : %s", kafkaHost, kafkaTopic));
        this.producer = new KafkaProducer<>(props);
        this.kafkaTopic = kafkaTopic;
    }


    public void sendEntry(JsonObject entry) {
        producer.send(new ProducerRecord<String, String>(kafkaTopic, entry.toString()));
    }

    public void close() {
        producer.close();
    }
}
