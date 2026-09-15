package com.desktopcat.server.catprofile;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("local")
public class LocalCatProfileRepository implements CatProfileRepository {
    private final AtomicReference<CatProfile> profile = new AtomicReference<>(
            new CatProfile(1L, "primary", "小饼干", 0, Instant.now(), Instant.now()));

    @Override
    public Optional<CatProfile> findPrimaryProfile() {
        return Optional.of(profile.get());
    }

    @Override
    public int updatePrimaryName(long profileId, String catName, int version) {
        while (true) {
            CatProfile current = profile.get();
            if (current.profileId() != profileId || current.version() != version) {
                return 0;
            }
            CatProfile updated = new CatProfile(
                    current.profileId(), current.profileKey(), catName, current.version() + 1,
                    current.createdAt(), Instant.now());
            if (profile.compareAndSet(current, updated)) {
                return 1;
            }
        }
    }
}
