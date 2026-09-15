package com.desktopcat.server.catprofile;

import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
public class MyBatisCatProfileRepository implements CatProfileRepository {
    private final CatProfileMapper mapper;

    public MyBatisCatProfileRepository(CatProfileMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<CatProfile> findPrimaryProfile() {
        return Optional.ofNullable(mapper.selectPrimaryProfile());
    }

    @Override
    public int updatePrimaryName(long profileId, String catName, int version) {
        return mapper.updatePrimaryName(profileId, catName, version);
    }
}
