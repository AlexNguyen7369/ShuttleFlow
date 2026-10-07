package com.shuttleflow.service;

import com.shuttleflow.dto.HomeDto;
import com.shuttleflow.repository.SlotRepository;
import org.springframework.stereotype.Service;

@Service
public class HomeService {

    private final SlotRepository slotRepository;

    public HomeService(SlotRepository slotRepository) {
        this.slotRepository = slotRepository;
    }

    public HomeDto getHomeInfo() {
        return new HomeDto("ShuttleFlow", slotRepository.countOpen());
    }
}
