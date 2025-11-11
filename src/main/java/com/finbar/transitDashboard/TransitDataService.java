package com.finbar.transitDashboard;

import com.finbar.transitDashboard.model.DelayHistoryRepository;
import com.finbar.transitDashboard.model.VehicleStatus;
import com.finbar.transitDashboard.model.DelayHistory;
import com.finbar.transitDashboard.model.VehicleStatusRepository;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TransitDataService {

    @Autowired
    private VehicleStatusRepository vehicleRepo;

    @Autowired
    private DelayHistoryRepository delayHistoryRepo;

    private VehicleStatus saveVehicleStatus(VehicleStatus vehicleStatus) {
        return vehicleRepo.save(vehicleStatus);
    }

    private List<VehicleStatus> fetchVehicleStatusList() {
        return vehicleRepo.findAll();
    }

    //@Autowired
    //private SimpMessagingTemplate websocket;

    private Map<String, Integer> tripDelays = new ConcurrentHashMap<>();

    @KafkaListener(topics = "trip-updates")
    public void processTripUpdate(byte[] data) throws Exception {
        System.out.println("TRIP UPDATE MESSAGE RECEIVED");
        List<TripUpdate> tripUpdateList;
        try {
            FeedMessage tripUpdateFeed = FeedMessage.parseFrom(data);
            tripUpdateList = tripUpdateFeed.getEntityList().stream()
                    .map(FeedEntity::getTripUpdate)
                    .toList();

            for (TripUpdate update : tripUpdateList) {
                int delay = update.getStopTimeUpdate(0).getArrival().getDelay();
                tripDelays.put(update.getTrip().getTripId(), delay);

                DelayHistory delayHistory = new DelayHistory();
                delayHistory.setTripId(update.getTrip().getTripId());
                delayHistory.setRouteId(update.getTrip().getRouteId());
                delayHistory.setDelaySeconds(delay);
                delayHistory.setTimestamp(update.getTimestamp());

                delayHistoryRepo.save(delayHistory);
            }

            List<DelayHistory> delayHistoryList = delayHistoryRepo.findAll();
            System.out.println("DELAY HISTORY DB size: " + delayHistoryList.size());
            System.out.println(delayHistoryList.subList(0, 5));


        } catch (Exception ex) {
            throw new Exception(ex.getMessage());
        }

    }

    @KafkaListener(topics = "vehicle-positions")
    public void processVehiclePosition(byte[] data) throws Exception {
        System.out.println("VEHICLE POSITION MESSAGE RECEIVED");
        List<VehiclePosition> vehiclePositionList;
        try {
            FeedMessage vehicleStatusFeed = FeedMessage.parseFrom(data);
            vehiclePositionList = vehicleStatusFeed.getEntityList().stream()
                    .map(FeedEntity::getVehicle)
                    .toList();

            for (VehiclePosition vehicle: vehiclePositionList) {
                VehicleStatus status = new VehicleStatus();
                status.setVehicleId(vehicle.getVehicle().getId());
                status.setTripId(vehicle.getTrip().getTripId());
                status.setRouteId(vehicle.getTrip().getRouteId());
                status.setLatitude(vehicle.getPosition().getLatitude());
                status.setLongitude(vehicle.getPosition().getLongitude());
                status.setStopId(vehicle.getStopId());
                status.setTimestamp(vehicle.getTimestamp());

                Integer delay = tripDelays.get(vehicle.getTrip().getTripId());
                status.setDelaySeconds(delay);

                vehicleRepo.save(status);
            }

            List<VehicleStatus> vehicleStatusList = vehicleRepo.findAll();
            System.out.println("VEHICLES STATUS DB SIZE: " + vehicleStatusList.size());
            System.out.println(vehicleStatusList.subList(0, 5));

        } catch (Exception ex) {
            throw new Exception(ex.getMessage());
        }

    }

}
