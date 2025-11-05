package com.finbar.transitDashboard;

import com.google.transit.realtime.GtfsRealtime;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import org.slf4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.LoggerFactory;

@Service
public class FeedPoller {
	private static Logger log = LoggerFactory.getLogger(FeedPoller.class);

	private int vehicleCount;

	public FeedPoller() {

	}

	// running every 30 seconds
	// @Scheduled(fixedRate = 30000)
	public void PollData() {

		RestClient restClient = RestClient.create();

		// TODO: parse data and send to kafka to topic as json rtd-bus-position topic
		byte[] protobufData = restClient.get()
				.uri("https://open-data.rtd-denver.com/files/gtfs-rt/rtd/VehiclePosition.pb")
				.retrieve()
				.body(byte[].class);

		// TODO: add kafka listener to read from kafka bus-position and store into in
		// memory
		// TODO: to calculate speed, you need to store previous vehicle position into
		// in-memoryDb like rocksDb
		// TODO: then when new kafka data comes in, get previous position as byte array
		// to string to calculate speed
		// TODO: store non realtime data in SQL or redis and Write API to fetch to
		// frontend (optional)
		// TODO: write enriched data and metrics back to kafka and SQL-lite or redis
		// TODO: read kafka enriched feed stream into UI with API
		// can use
		try {
			System.out.println(protobufData[0]);
			System.out.println(protobufData[1]);
			System.out.println(protobufData[2]);

			FeedMessage feed = FeedMessage.parseFrom(protobufData);

			FeedEntity feedEntity = feed.getEntity(0);
			VehiclePosition position = feedEntity.getVehicle();
			System.out.println(position);

			System.out.println("---------------------");
			GtfsRealtime.Position actualPosition = position.getPosition();

			Set<String> allVehicleIds = new HashSet<>();

			for (FeedEntity entity : feed.getEntityList()) {
				if (entity.hasVehicle()) {
					VehiclePosition vehicle = entity.getVehicle();
					if (vehicle.hasTrip()) {
						String tripId = vehicle.getTrip().getTripId();
						String routeId = vehicle.getTrip().getRouteId();
						int directionId = vehicle.getTrip().getDirectionId();
						String scheduleRelationship = vehicle.getTrip().getScheduleRelationship().toString();
						LocalDateTime currentDateTime = LocalDateTime.now();
						//System.out.println(currentDateTime + "Trip: " + tripId + ", Route: " + routeId);

					}
					if (vehicle.hasVehicle()) {
						String id = vehicle.getVehicle().getId();
                       	allVehicleIds.add(id);
					}
				}

				if (entity.hasAlert()) {
					GtfsRealtime.Alert alert = entity.getAlert();
					System.out.println(alert);

				}

			}

			System.out.println("Unique vehicle count " + allVehicleIds.size());

			vehicleCount = allVehicleIds.size();

		} catch (Exception ex) {
			ex.printStackTrace();
		}

	}

	public int getVehicleCount () {
		return vehicleCount;
	}
}
