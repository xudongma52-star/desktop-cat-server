package com.desktopcat.server.catprofile;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class CatProfileControllerTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void readsUpdatesAndProtectsTheProfileFromStaleWrites() throws Exception {
        String initialJson = mvc.perform(get("/api/cat/profile"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", matchesPattern("[a-z0-9]{32}")))
                .andExpect(jsonPath("$.profileId").isNumber())
                .andExpect(jsonPath("$.catName").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        JsonNode initial = objectMapper.readTree(initialJson);
        long profileId = initial.get("profileId").asLong();
        int version = initial.get("version").asInt();

        mvc.perform(patch("/api/cat/profile/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"profileId":%d,"catName":"  煤球  ","version":%d}
                                """.formatted(profileId, version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(profileId))
                .andExpect(jsonPath("$.catName").value("煤球"))
                .andExpect(jsonPath("$.version").value(version + 1));

        mvc.perform(patch("/api/cat/profile/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"profileId":%d,"catName":"小饼干","version":%d}
                                """.formatted(profileId, version)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAT_PROFILE_VERSION_CONFLICT"))
                .andExpect(jsonPath("$.message").value(
                        "Cat profile has been updated. Refresh and try again."))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/cat/profile/name"));
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
