package com.desktopcat.server.catprofile;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.desktopcat.server.catprofile.controller.CatProfileController;
import com.desktopcat.server.catprofile.dao.CatProfileDO;
import com.desktopcat.server.catprofile.dao.CatProfileDao;
import com.desktopcat.server.catprofile.service.CatProfileService;
import com.desktopcat.server.catprofile.service.impl.CatProfileServiceImpl;
import com.desktopcat.server.events.AssistantEventStream;
import com.desktopcat.server.web.GlobalExceptionHandler;
import com.desktopcat.server.web.RequestIdFilter;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CatProfileControllerTest {
    private final CatProfileDao catProfileDao = mock(CatProfileDao.class);
    private final AssistantEventStream eventStream = mock(AssistantEventStream.class);
    private final AtomicReference<CatProfileDO> profile = new AtomicReference<>();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        profile.set(new CatProfileDO(1L, "primary", "小饼干", 0, Instant.now(), Instant.now()));
        when(catProfileDao.selectPrimaryProfile()).thenAnswer(invocation -> profile.get());
        when(catProfileDao.updatePrimaryName(anyLong(), anyString(), anyInt())).thenAnswer(invocation -> {
            long profileId = invocation.getArgument(0);
            String catName = invocation.getArgument(1);
            int version = invocation.getArgument(2);
            CatProfileDO current = profile.get();
            if (current.profileId() != profileId || current.version() != version) return 0;
            profile.set(new CatProfileDO(
                    current.profileId(), current.profileKey(), catName, current.version() + 1,
                    current.createdAt(), Instant.now()));
            return 1;
        });

        CatProfileService service = new CatProfileServiceImpl(catProfileDao, eventStream);
        mvc = MockMvcBuilders.standaloneSetup(new CatProfileController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void readsUpdatesAndProtectsTheProfileFromStaleWrites() throws Exception {
        mvc.perform(get("/api/cat/profile"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", matchesPattern("[a-z0-9]{32}")))
                .andExpect(jsonPath("$.profileId").value(1))
                .andExpect(jsonPath("$.catName").value("小饼干"))
                .andExpect(jsonPath("$.version").value(0));

        mvc.perform(patch("/api/cat/profile/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"profileId":1,"catName":"  煤球  ","version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(1))
                .andExpect(jsonPath("$.catName").value("煤球"))
                .andExpect(jsonPath("$.version").value(1));

        verify(eventStream).publishCatProfileUpdated(1L, 1);

        mvc.perform(patch("/api/cat/profile/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"profileId":1,"catName":"小饼干","version":0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAT_PROFILE_VERSION_CONFLICT"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void reportsSpecificValidationErrors() throws Exception {
        mvc.perform(patch("/api/cat/profile/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"profileId":1,"catName":"   ","version":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAT_NAME_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Cat name is required."));
    }
}
