package com.finbar.transitDashboard;


import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class FeedPoller {

    public FeedPoller() {}

    public void PollData() {

        RestClient restClient = RestClient.create();

        byte[] protobufData = restClient.get()
                .uri("https://open-data.rtd-denver.com/files/gtfs-rt/rtd/VehiclePosition.pb")
                .retrieve()
                .body(byte[].class);

        try {
            FeedMessage feed = FeedMessage.parseFrom(protobufData);

            for (FeedEntity entity : feed.getEntityList()) {
                if (entity.hasVehicle()) {
                    VehiclePosition vehicle = entity.getVehicle();

                    if (vehicle.hasTrip()) {
                        String tripId = vehicle.getTrip().getTripId();
                        String routeId = vehicle.getTrip().getRouteId();
                        int directionId = vehicle.getTrip().getDirectionId();
                        String scheduleRelationship = vehicle.getTrip().getScheduleRelationship().toString();
                        System.out.println("Trip: " + tripId + ", Route: " + routeId);
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }
}
