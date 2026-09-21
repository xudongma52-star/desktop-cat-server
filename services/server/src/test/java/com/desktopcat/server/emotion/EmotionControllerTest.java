package com.desktopcat.server.emotion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.desktopcat.server.emotion.controller.EmotionController;
import com.desktopcat.server.identity.application.CurrentUserService;
import com.desktopcat.server.emotion.dao.EmotionDao;
import com.desktopcat.server.emotion.dao.EmotionDO;
import com.desktopcat.server.emotion.service.EmotionService;
import com.desktopcat.server.emotion.service.impl.EmotionServiceImpl;
import com.desktopcat.server.web.GlobalExceptionHandler;
import com.desktopcat.server.web.RequestIdFilter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EmotionControllerTest {
    private static final long USER_ID = 1L;
    private final EmotionDao emotionDao = mock(EmotionDao.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            EmotionDO emotion = invocation.getArgument(0);
            emotion.setEmotionId(1L);
            return 1;
        }).when(emotionDao).insert(any(EmotionDO.class));

        EmotionService service = new EmotionServiceImpl(emotionDao);
        when(currentUserService.requireUserId(nullable(String.class))).thenReturn(USER_ID);
        mvc = MockMvcBuilders.standaloneSetup(
                        new EmotionController(service, currentUserService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void createsAnEmotionForTheCurrentShanghaiDateAndIgnoresClientDates() throws Exception {
        mvc.perform(post("/api/emotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content":"  今天事情都挤在一起，真的很烦。  ",
                                  "recordDate":"2099-01-01"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emotionId").value(1))
                .andExpect(jsonPath("$.content").value("今天事情都挤在一起，真的很烦。"))
                .andExpect(jsonPath("$.recordDate").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        ArgumentCaptor<EmotionDO> emotionCaptor = ArgumentCaptor.forClass(EmotionDO.class);
        verify(emotionDao).insert(emotionCaptor.capture());
        assertEquals(
                LocalDate.now(ZoneId.of("Asia/Shanghai")),
                emotionCaptor.getValue().getRecordDate());
        assertEquals(USER_ID, emotionCaptor.getValue().getUserId());
    }

    @Test
    void listsEmotionsInTimeOrderForTheRequestedDate() throws Exception {
        LocalDate date = LocalDate.of(2026, 9, 16);
        when(emotionDao.selectByDate(USER_ID, date)).thenReturn(List.of(
                new EmotionDO(1L, USER_ID, "上午有点困。", date,
                        Instant.parse("2026-09-16T01:00:00Z")),
                new EmotionDO(2L, USER_ID, "晚上轻松多了。", date,
                        Instant.parse("2026-09-16T13:00:00Z"))));

        mvc.perform(get("/api/emotions?date=2026-09-16"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].emotionId").value(1))
                .andExpect(jsonPath("$[1].emotionId").value(2));

        verify(emotionDao).selectByDate(USER_ID, date);
    }

    @Test
    void rejectsBlankContentAndInvalidDates() throws Exception {
        mvc.perform(post("/api/emotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMOTION_CONTENT_REQUIRED"));

        mvc.perform(get("/api/emotions?date=not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMOTION_DATE_INVALID"));
    }
}
