package com.desktopcat.server.events.controller;

import com.desktopcat.server.events.AssistantEventStream;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** SSE 事件订阅入口，连接管理和事件发布由 AssistantEventStream 负责。 */
@RestController
public class EventStreamController {
    private final AssistantEventStream eventStream;

    public EventStreamController(AssistantEventStream eventStream) {
        this.eventStream = eventStream;
    }

    //响应类型为 text/event-stream，即 SSE 事件流
    @GetMapping(path = "/api/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        return eventStream.subscribe();
    }
}
