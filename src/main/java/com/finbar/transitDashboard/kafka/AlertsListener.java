package com.finbar.transitDashboard.kafka;

import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@KafkaListener(topics = "alerts")
public class AlertsListener {

    @KafkaHandler(isDefault = true)
    public void listen(byte[] data) throws Exception {
        try {
            FeedMessage alertsFeed = FeedMessage.parseFrom(data);
            FeedEntity entity = alertsFeed.getEntity(0);
            System.out.println("ALERT");
            System.out.println(entity);
            System.out.println("----------------------------------------");
        } catch (Exception ex) {
            throw new Exception(ex.getMessage());
        }
    }
}
