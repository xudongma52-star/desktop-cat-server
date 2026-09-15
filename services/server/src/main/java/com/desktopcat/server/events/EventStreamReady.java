package com.desktopcat.server.events;

import java.time.Instant;

public record EventStreamReady(Instant connectedAt) {
}
