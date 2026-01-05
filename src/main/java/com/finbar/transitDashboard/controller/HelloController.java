package com.finbar.transitDashboard.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.finbar.transitDashboard.FeedPoller;

@RestController
public class HelloController {

    private final FeedPoller feedPoller;

    public HelloController(FeedPoller feedPoller) {
        this.feedPoller = feedPoller;
    }


    @GetMapping("/hello")
    public String index() {
        return "Hello from RTD dashboard!";
    }

}
