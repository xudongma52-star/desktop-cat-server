package com.desktopcat.server.reminder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.desktopcat.server.reminder.controller.ReminderController;
import com.desktopcat.server.reminder.dao.ReminderDO;
import com.desktopcat.server.reminder.dao.ReminderDao;
import com.desktopcat.server.reminder.service.ReminderService;
import com.desktopcat.server.reminder.service.impl.ReminderServiceImpl;
import com.desktopcat.server.web.GlobalExceptionHandler;
import com.desktopcat.server.web.RequestIdFilter;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReminderControllerTest {
    private final ReminderDao reminderDao = mock(ReminderDao.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final AtomicReference<ReminderDO> stored = new AtomicReference<>();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        stored.set(null);
        doAnswer(invocation -> {
            ReminderDO reminder = invocation.getArgument(0);
            Instant now = Instant.parse("2026-09-17T01:00:00Z");
            reminder.setReminderId(1L);
            reminder.setStatus("PENDING");
            reminder.setVersion(0);
            reminder.setCreatedAt(now);
            reminder.setUpdatedAt(now);
            stored.set(reminder);
            return 1;
        }).when(reminderDao).insertReminder(any(ReminderDO.class));
        when(reminderDao.selectActiveById(anyLong())).thenAnswer(invocation -> {
            ReminderDO reminder = stored.get();
            long reminderId = invocation.getArgument(0);
            return reminder != null && reminder.getReminderId() == reminderId ? reminder : null;
        });
        when(reminderDao.selectActiveList(any(), any(), any())).thenAnswer(invocation -> {
            ReminderDO reminder = stored.get();
            if (reminder == null) return List.of();
            String status = invocation.getArgument(0);
            return status == null || status.equals(reminder.getStatus())
                    ? List.of(reminder)
                    : List.of();
        });
        when(reminderDao.updateReminder(any(ReminderDO.class))).thenAnswer(invocation -> {
            ReminderDO update = invocation.getArgument(0);
            ReminderDO current = stored.get();
            if (current == null || !"PENDING".equals(current.getStatus())
                    || !current.getVersion().equals(update.getVersion())) return 0;
            current.setContent(update.getContent());
            current.setRemindAt(update.getRemindAt());
            current.setVersion(current.getVersion() + 1);
            current.setUpdatedAt(Instant.parse("2026-09-17T01:01:00Z"));
            return 1;
        });
        when(reminderDao.completeReminder(anyLong(), anyInt())).thenAnswer(invocation -> {
            ReminderDO current = stored.get();
            int version = invocation.getArgument(1);
            if (current == null || !"PENDING".equals(current.getStatus())
                    || current.getVersion() != version) return 0;
            current.setStatus("COMPLETED");
            current.setCompletedAt(Instant.parse("2026-09-17T01:02:00Z"));
            current.setVersion(current.getVersion() + 1);
            return 1;
        });
        when(reminderDao.logicalDelete(anyLong(), anyInt())).thenAnswer(invocation -> {
            ReminderDO current = stored.get();
            int version = invocation.getArgument(1);
            if (current == null || current.getVersion() != version) return 0;
            stored.set(null);
            return 1;
        });

        ReminderService service = new ReminderServiceImpl(reminderDao, eventPublisher);
        mvc = MockMvcBuilders.standaloneSetup(new ReminderController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void completesTheReminderLifecycle() throws Exception {
        mvc.perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"  记得喝水  ","remindAt":"2099-09-17T08:30:00Z"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reminderId").value(1))
                .andExpect(jsonPath("$.content").value("记得喝水"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.version").value(0));

        mvc.perform(get("/api/reminders?scope=PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reminderId").value(1));

        mvc.perform(put("/api/reminders/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"起来活动一下","remindAt":"2099-09-17T09:00:00Z","version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("起来活动一下"))
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(patch("/api/reminders/1/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.version").value(2));

        mvc.perform(delete("/api/reminders/1?version=2"))
                .andExpect(status().isNoContent());
    }

    @Test
    void returnsSpecificValidationAndConflictErrors() throws Exception {
        mvc.perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"   ","remindAt":"2099-09-17T08:30:00Z"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REMINDER_CONTENT_REQUIRED"));

        mvc.perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"已经过去的提醒","remindAt":"2020-01-01T00:00:00Z"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REMINDER_TIME_IN_PAST"));

        mvc.perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"喝水","remindAt":"2099-09-17T08:30:00Z"}
                                """))
                .andExpect(status().isCreated());

        mvc.perform(put("/api/reminders/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"过去的修改时间","remindAt":"2020-01-01T00:00:00Z","version":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REMINDER_TIME_IN_PAST"));

        mvc.perform(put("/api/reminders/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"喝水","remindAt":"2099-09-17T09:00:00Z","version":9}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REMINDER_VERSION_CONFLICT"));

        mvc.perform(get("/api/reminders?scope=UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REMINDER_SCOPE_INVALID"));
    }
}
