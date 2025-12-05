package com.finbar.transitDashboard;

import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.protocol.Message;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;

import org.slf4j.LoggerFactory;

@Service
public class FeedPoller {

	private static Logger log = LoggerFactory.getLogger(FeedPoller.class);

	public FeedPoller() {}

	@Bean
	private Map<String, Object> producerConfigs() {
		Map<String, Object> props = new HashMap<>();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
		props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
		return props;
	}

	@Autowired
	private KafkaTemplate<Integer, byte[]> kafkaTemplate;

	// running every 30 seconds
	@Scheduled(fixedRate = 30000)
	public void PollData() {

		RestClient restClient = RestClient.create();

		try {
			byte[] vehicleFeed = restClient.get()
					.uri("https://open-data.rtd-denver.com/files/gtfs-rt/rtd/VehiclePosition.pb")
					.retrieve()
					.body(byte[].class);

			byte[] alertsFeed = restClient.get()
					.uri("https://open-data.rtd-denver.com/files/gtfs-rt/rtd/Alerts.pb")
					.retrieve()
					.body(byte[].class);


			byte[] tripUpdateFeed = restClient.get()
					.uri("https://open-data.rtd-denver.com/files/gtfs-rt/rtd/TripUpdate.pb")
					.retrieve()
					.body(byte[].class);


			System.out.println("SENDING VEHICLE FEED");
			kafkaTemplate.send("vehicle-positions", vehicleFeed);
			System.out.println("SENDING TRIP UPDATE FEED");
			kafkaTemplate.send("trip-updates", tripUpdateFeed);



		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

}
