package com.finbar.transitDashboard.kafka;

import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@KafkaListener(topics = "vehicle-positions")
public class VehiclePositionListener {

    private List<VehiclePosition> vehiclePositionList;

    @KafkaHandler(isDefault = true)
    public void listen(byte[] data) throws Exception {
        try {
            FeedMessage vehicleFeed = FeedMessage.parseFrom(data);
            vehiclePositionList = vehicleFeed.getEntityList().stream()
                    .map(FeedEntity::getVehicle)
                    .collect(Collectors.toList());

        } catch (Exception ex) {
            throw new Exception(ex.getMessage());
        }
    }

    public List<VehiclePosition> getVehiclePositionList() { return vehiclePositionList; }
}
