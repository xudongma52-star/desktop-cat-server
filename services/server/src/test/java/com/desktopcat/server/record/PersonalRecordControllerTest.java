package com.desktopcat.server.record;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.desktopcat.server.record.controller.PersonalRecordController;
import com.desktopcat.server.record.dao.PersonalRecordActivityDO;
import com.desktopcat.server.record.dao.PersonalRecordDO;
import com.desktopcat.server.record.dao.PersonalRecordDao;
import com.desktopcat.server.record.service.PersonalRecordService;
import com.desktopcat.server.record.service.impl.PersonalRecordServiceImpl;
import com.desktopcat.server.web.GlobalExceptionHandler;
import com.desktopcat.server.web.RequestIdFilter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PersonalRecordControllerTest {
    private final PersonalRecordDao personalRecordDao = mock(PersonalRecordDao.class);
    private final Map<Long, PersonalRecordDO> records = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        records.clear();
        sequence.set(0);

        when(personalRecordDao.insertRecord(any(PersonalRecordDO.class))).thenAnswer(invocation -> {
            PersonalRecordDO requested = invocation.getArgument(0);
            long recordId = sequence.incrementAndGet();
            requested.setRecordId(recordId);
            Instant now = Instant.parse("2026-09-16T08:00:00Z");
            records.put(recordId, copyWithState(requested, 0, now, now));
            return 1;
        });
        when(personalRecordDao.selectActiveById(anyLong())).thenAnswer(invocation -> {
            PersonalRecordDO record = records.get(invocation.<Long>getArgument(0));
            return record == null ? null : copy(record);
        });
        when(personalRecordDao.countActive(any(), any())).thenAnswer(invocation ->
                (long) filteredRecords(invocation.getArgument(0), invocation.getArgument(1)).size());
        when(personalRecordDao.selectActivePage(any(), any(), anyInt(), anyInt())).thenAnswer(invocation -> {
            List<PersonalRecordDO> filtered = filteredRecords(
                    invocation.getArgument(0), invocation.getArgument(1));
            int offset = invocation.getArgument(2);
            int limit = invocation.getArgument(3);
            return filtered.stream().skip(offset).limit(limit).map(this::copy).toList();
        });
        when(personalRecordDao.selectDailyActivity(any(), any(), any())).thenAnswer(invocation -> {
            String recordType = invocation.getArgument(0);
            LocalDate startDate = invocation.getArgument(1);
            LocalDate endDate = invocation.getArgument(2);
            Map<LocalDate, Long> counts = filteredRecords(recordType, null).stream()
                    .filter(record -> !record.getRecordDate().isBefore(startDate))
                    .filter(record -> !record.getRecordDate().isAfter(endDate))
                    .collect(Collectors.groupingBy(
                            PersonalRecordDO::getRecordDate, TreeMap::new, Collectors.counting()));
            return counts.entrySet().stream()
                    .map(entry -> new PersonalRecordActivityDO(entry.getKey(), entry.getValue()))
                    .toList();
        });
        when(personalRecordDao.updateRecord(any(PersonalRecordDO.class))).thenAnswer(invocation -> {
            PersonalRecordDO requested = invocation.getArgument(0);
            PersonalRecordDO current = records.get(requested.getRecordId());
            if (current == null || !current.getVersion().equals(requested.getVersion())) {
                return 0;
            }
            records.put(requested.getRecordId(), copyWithState(
                    requested, current.getVersion() + 1, current.getCreatedAt(),
                    Instant.parse("2026-09-16T09:00:00Z")));
            return 1;
        });
        when(personalRecordDao.logicalDelete(anyLong(), anyInt())).thenAnswer(invocation -> {
            long recordId = invocation.getArgument(0);
            int version = invocation.getArgument(1);
            PersonalRecordDO current = records.get(recordId);
            if (current == null || current.getVersion() != version) {
                return 0;
            }
            records.remove(recordId);
            return 1;
        });

        PersonalRecordService service = new PersonalRecordServiceImpl(personalRecordDao);
        mvc = MockMvcBuilders.standaloneSetup(new PersonalRecordController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void createsListsUpdatesAndDeletesARecordWithOptimisticLocking() throws Exception {
        mvc.perform(post("/api/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recordType":"diary",
                                  "title":"  今天很好  ",
                                  "content":"完成了第一篇记录。",
                                  "recordDate":"2026-09-16",
                                  "mood":"  开心  ",
                                  "recallEnabled":true,
                                  "ragEnabled":false
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recordId").value(1))
                .andExpect(jsonPath("$.recordType").value("DIARY"))
                .andExpect(jsonPath("$.title").value("今天很好"))
                .andExpect(jsonPath("$.mood").value("开心"))
                .andExpect(jsonPath("$.version").value(0));

        mvc.perform(get("/api/records?page=1&pageSize=12&recordType=DIARY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].excerpt").value("完成了第一篇记录。"))
                .andExpect(jsonPath("$.items[0]", not(hasKey("content"))))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        mvc.perform(get("/api/records/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("完成了第一篇记录。"));

        mvc.perform(put("/api/records/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recordType":"THOUGHT",
                                  "title":null,
                                  "content":"修改后的内容",
                                  "recordDate":"2026-09-16",
                                  "mood":null,
                                  "recallEnabled":false,
                                  "ragEnabled":true,
                                  "version":0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordType").value("THOUGHT"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.ragEnabled").value(true));

        mvc.perform(delete("/api/records/1?version=0"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PERSONAL_RECORD_VERSION_CONFLICT"));

        mvc.perform(delete("/api/records/1?version=1"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/records/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERSONAL_RECORD_NOT_FOUND"));
    }

    @Test
    void reportsSpecificValidationAndNotFoundErrors() throws Exception {
        mvc.perform(post("/api/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recordType":"DIARY",
                                  "content":"   ",
                                  "recordDate":"2026-09-16"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECORD_CONTENT_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Record content is required."));

        mvc.perform(get("/api/records?page=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAGE_INVALID"));

        mvc.perform(get("/api/records/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERSONAL_RECORD_NOT_FOUND"));
    }

    @Test
    void preservesRequestBodyAndVersionValidationAcrossTheServiceBoundary() throws Exception {
        mvc.perform(post("/api/records")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_BODY_REQUIRED"));

        mvc.perform(put("/api/records/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recordType":"DIARY",
                                  "content":"缺少版本号",
                                  "recordDate":"2026-09-16",
                                  "recallEnabled":false,
                                  "ragEnabled":false
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECORD_VERSION_REQUIRED"));
    }

    @Test
    void reportsInvalidNumericParametersAsBadRequests() throws Exception {
        mvc.perform(get("/api/records/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECORD_ID_INVALID"))
                .andExpect(jsonPath("$.message").value("Record id must be a number."));

        mvc.perform(get("/api/records?page=not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAGE_INVALID"))
                .andExpect(jsonPath("$.message").value("Page must be a number."));

        mvc.perform(delete("/api/records/1?version=not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECORD_VERSION_INVALID"))
                .andExpect(jsonPath("$.message").value("Record version must be a number."));

        mvc.perform(get("/api/records/recalls?limit=not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECALL_LIMIT_INVALID"))
                .andExpect(jsonPath("$.message").value("Recall limit must be a number."));
    }

    @Test
    void returnsOnlyEnabledRecallArticlesAndNeverReturnsTheirFullContent() throws Exception {
        createRecord("参与回忆", "第一段\n第二段 " + "很温暖".repeat(60), true, "2026-09-16");
        createRecord("普通文章", "这篇文章不参加轮播", false, "2026-09-15");

        mvc.perform(get("/api/records/recalls?limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("参与回忆"))
                .andExpect(jsonPath("$[0].excerpt").isNotEmpty())
                .andExpect(jsonPath("$[0].excerpt").value(org.hamcrest.Matchers.endsWith("…")))
                .andExpect(jsonPath("$[0]", not(hasKey("content"))));
    }

    @Test
    void returnsDailyWritingActivityWithoutLoadingArticleContent() throws Exception {
        createRecord("九月十六日之一", "今天的第一篇日记", false, "2026-09-16");
        createRecord("九月十六日之二", "今天的第二篇日记", false, "2026-09-16");
        createRecord("九月十七日", "新的一天", false, "2026-09-17");

        mvc.perform(get("/api/records/activity")
                        .param("startDate", "2026-09-01")
                        .param("endDate", "2026-09-30")
                        .param("recordType", "DIARY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value("2026-09-01"))
                .andExpect(jsonPath("$.endDate").value("2026-09-30"))
                .andExpect(jsonPath("$.totalRecords").value(3))
                .andExpect(jsonPath("$.activeDays").value(2))
                .andExpect(jsonPath("$.days", hasSize(2)))
                .andExpect(jsonPath("$.days[0].recordDate").value("2026-09-16"))
                .andExpect(jsonPath("$.days[0].recordCount").value(2))
                .andExpect(jsonPath("$.days[1].recordDate").value("2026-09-17"))
                .andExpect(jsonPath("$.days[1].recordCount").value(1))
                .andExpect(jsonPath("$.days[0]", not(hasKey("content"))));
    }

    @Test
    void rejectsInvalidWritingActivityDateRanges() throws Exception {
        mvc.perform(get("/api/records/activity")
                        .param("startDate", "2026-09-17")
                        .param("endDate", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACTIVITY_DATE_RANGE_INVALID"));

        mvc.perform(get("/api/records/activity")
                        .param("startDate", "2025-01-01")
                        .param("endDate", "2026-09-17"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACTIVITY_DATE_RANGE_TOO_LARGE"));
    }

    private void createRecord(String title, String content, boolean recallEnabled, String recordDate)
            throws Exception {
        String requestBody = """
                {
                  "recordType":"DIARY",
                  "title":"%s",
                  "content":"%s",
                  "recordDate":"%s",
                  "recallEnabled":%s,
                  "ragEnabled":false
                }
                """.formatted(title, content.replace("\n", "\\n"), recordDate, recallEnabled);
        mvc.perform(post("/api/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());
    }

    private List<PersonalRecordDO> filteredRecords(String recordType, Boolean recallEnabled) {
        Comparator<PersonalRecordDO> ordering = Comparator
                .comparing(PersonalRecordDO::getRecordDate)
                .thenComparing(PersonalRecordDO::getRecordId)
                .reversed();
        return records.values().stream()
                .filter(record -> recordType == null || recordType.equals(record.getRecordType()))
                .filter(record -> recallEnabled == null || recallEnabled.equals(record.getRecallEnabled()))
                .sorted(ordering)
                .toList();
    }

    private PersonalRecordDO copy(PersonalRecordDO source) {
        return new PersonalRecordDO(
                source.getRecordId(), source.getRecordType(), source.getTitle(), source.getContent(),
                source.getRecordDate(), source.getMood(), source.getRecallEnabled(), source.getRagEnabled(),
                source.getVersion(), source.getCreatedAt(), source.getUpdatedAt());
    }

    private PersonalRecordDO copyWithState(
            PersonalRecordDO source, int version, Instant createdAt, Instant updatedAt) {
        PersonalRecordDO copy = copy(source);
        copy.setVersion(version);
        copy.setCreatedAt(createdAt);
        copy.setUpdatedAt(updatedAt);
        return copy;
    }
}
