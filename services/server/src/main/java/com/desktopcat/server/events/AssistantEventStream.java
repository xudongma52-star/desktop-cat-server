package com.desktopcat.server.events;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class AssistantEventStream {
    private static final Logger log = LoggerFactory.getLogger(AssistantEventStream.class);
    private static final long NO_SERVER_TIMEOUT = 0L;

    private final Map<String, SseEmitter> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe() {
        String subscriberId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(NO_SERVER_TIMEOUT);
        subscribers.put(subscriberId, emitter);
        emitter.onCompletion(() -> removeSubscriber(subscriberId));
        emitter.onTimeout(() -> removeSubscriber(subscriberId));
        emitter.onError(error -> removeSubscriber(subscriberId));

        try {
            emitter.send(SseEmitter.event()
                    .name("connection.ready")
                    .data(new EventStreamReady(Instant.now())));
            log.debug("event=sse_subscriber_connected subscriberCount={}", subscribers.size());
        } catch (IOException exception) {
            removeSubscriber(subscriberId);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    public void publishCatProfileUpdated(long profileId, int version) {
        publish(SseEmitter.event()
                .id(profileId + "-" + version)
                .name("cat-profile.updated")
                .data(new CatProfileUpdatedEvent(profileId, version)));
    }

    @Scheduled(fixedDelayString = "${app.events.heartbeat-ms:25000}")
    void sendHeartbeat() {
        publish(SseEmitter.event().comment("keepalive"));
    }

    private void publish(SseEmitter.SseEventBuilder event) {
        subscribers.forEach((subscriberId, emitter) -> {
            try {
                emitter.send(event);
            } catch (IOException | IllegalStateException exception) {
                removeSubscriber(subscriberId);
                emitter.complete();
            }
        });
    }

    private void removeSubscriber(String subscriberId) {
        if (subscribers.remove(subscriberId) != null) {
            log.debug("event=sse_subscriber_disconnected subscriberCount={}", subscribers.size());
        }
    }
}
