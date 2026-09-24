package com.shuttleflow.controller;

import com.shuttleflow.dto.HomeDto;
import com.shuttleflow.service.HomeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @GetMapping("/")
    public HomeDto home() {
        return homeService.getHomeInfo();
    }
}
