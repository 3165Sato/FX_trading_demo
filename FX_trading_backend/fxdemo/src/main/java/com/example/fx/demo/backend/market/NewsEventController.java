package com.example.fx.demo.backend.market;

import com.example.fx.demo.backend.market.dto.NewsEventRequest;
import com.example.fx.demo.backend.market.dto.NewsEventResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/market/news/events")
public class NewsEventController {

    private final NewsEventService newsEventService;

    public NewsEventController(NewsEventService newsEventService) {
        this.newsEventService = newsEventService;
    }

    @PostMapping
    public NewsEventResponse trigger(@RequestBody NewsEventRequest request) {
        return newsEventService.trigger(request);
    }

    @GetMapping
    public List<NewsEventResponse> listEvents(
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return newsEventService.listEvents(activeOnly, limit);
    }
}
