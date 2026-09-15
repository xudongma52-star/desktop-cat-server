package com.desktopcat.server.events;

public record CatProfileUpdatedEvent(long profileId, int version) {
}
