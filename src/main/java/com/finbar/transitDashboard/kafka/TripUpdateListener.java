package com.finbar.transitDashboard.kafka;

import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@KafkaListener(topics = "trip-updates")
public class TripUpdateListener {

    private List<TripUpdate> tripUpdateList;

    @KafkaHandler(isDefault = true)
    public void listen(byte[] data) throws Exception {
        try {
            FeedMessage tripUpdateFeed = FeedMessage.parseFrom(data);
            tripUpdateList = tripUpdateFeed.getEntityList().stream()
                    .map(FeedEntity::getTripUpdate)
                    .collect(Collectors.toList());

        } catch (Exception ex) {
            throw new Exception(ex.getMessage());
        }
    }

    public List<TripUpdate> getTripUpdateList() { return tripUpdateList; }

}
