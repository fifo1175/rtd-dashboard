package com.finbar.transitDashboard;


import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class FeedPoller {

    private void PollData() {

        RestClient restClient = RestClient.create();

        String result = restClient.get()
                .uri("https://open-data.rtd-denver.com/files/gtfs-rt/rtd/VehiclePosition.pb")
                .retrieve()
                .body(String.class);


    }
}
