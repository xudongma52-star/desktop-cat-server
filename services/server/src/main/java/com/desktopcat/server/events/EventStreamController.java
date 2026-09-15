package com.desktopcat.server.events;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class EventStreamController {
    private final AssistantEventStream eventStream;

    public EventStreamController(AssistantEventStream eventStream) {
        this.eventStream = eventStream;
    }

    @GetMapping(path = "/api/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        return eventStream.subscribe();
    }
}
