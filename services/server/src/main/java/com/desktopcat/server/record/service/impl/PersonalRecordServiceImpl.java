package com.desktopcat.server.record.service.impl;

import com.desktopcat.server.record.dao.PersonalRecordDO;
import com.desktopcat.server.record.dao.PersonalRecordDao;
import com.desktopcat.server.record.service.PersonalRecordPage;
import com.desktopcat.server.record.service.PersonalRecordRecall;
import com.desktopcat.server.record.service.PersonalRecordService;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("postgres")
public class PersonalRecordServiceImpl implements PersonalRecordService {
    private static final Logger log = LoggerFactory.getLogger(PersonalRecordServiceImpl.class);
    private static final Set<String> RECORD_TYPES = Set.of("DIARY", "THOUGHT", "WORK_NOTE");
    private static final int MAX_TITLE_LENGTH = 120;
    private static final int MAX_CONTENT_LENGTH = 100_000;
    private static final int MAX_MOOD_LENGTH = 32;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_RECALL_LIMIT = 50;
    private static final int EXCERPT_LENGTH = 160;

    private final PersonalRecordDao personalRecordDao;

    public PersonalRecordServiceImpl(PersonalRecordDao personalRecordDao) {
        this.personalRecordDao = personalRecordDao;
    }

    @Override
    @Transactional
    public PersonalRecordDO createRecord(PersonalRecordDO record) {
        validateAndNormalizeRecord(record, false);
        record.setRecordId(null);
        record.setVersion(0);

        int insertedRows = personalRecordDao.insertRecord(record);
        if (insertedRows != 1 || record.getRecordId() == null) {
            log.error("event=personal_record_create_failed recordType={} contentLength={} recallEnabled={} ragEnabled={}",
                    record.getRecordType(), codePointLength(record.getContent()),
                    record.getRecallEnabled(), record.getRagEnabled());
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Personal record could not be created.");
        }

        PersonalRecordDO created = findActiveRecord(record.getRecordId());
        log.info("event=personal_record_created recordId={} recordType={} version={} contentLength={} "
                        + "recallEnabled={} ragEnabled={}",
                created.getRecordId(), created.getRecordType(), created.getVersion(),
                codePointLength(created.getContent()), created.getRecallEnabled(), created.getRagEnabled());
        return created;
    }

    @Override
    public PersonalRecordPage listRecords(Integer page, Integer pageSize, String recordType) {
        int normalizedPage = validatePage(page);
        int normalizedPageSize = validatePageSize(pageSize);
        String normalizedRecordType = normalizeOptionalRecordType(recordType);
        long offsetValue = (long) (normalizedPage - 1) * normalizedPageSize;
        if (offsetValue > Integer.MAX_VALUE) {
            throw badRequest("Requested page is too large.");
        }

        long total = personalRecordDao.countActive(normalizedRecordType, null);
        List<PersonalRecordDO> items = personalRecordDao.selectActivePage(
                normalizedRecordType, null, (int) offsetValue, normalizedPageSize);
        long totalPages = total == 0 ? 0 : (total + normalizedPageSize - 1) / normalizedPageSize;
        return new PersonalRecordPage(List.copyOf(items), normalizedPage,
                normalizedPageSize, total, totalPages);
    }

    @Override
    public PersonalRecordDO getRecord(Long recordId) {
        return findActiveRecord(validateRecordId(recordId));
    }

    @Override
    @Transactional
    public PersonalRecordDO updateRecord(Long recordId, PersonalRecordDO record) {
        long normalizedRecordId = validateRecordId(recordId);
        validateAndNormalizeRecord(record, true);
        PersonalRecordDO current = findActiveRecord(normalizedRecordId);
        record.setRecordId(normalizedRecordId);

        int updatedRows = personalRecordDao.updateRecord(record);
        if (updatedRows == 0) {
            PersonalRecordDO latest = personalRecordDao.selectActiveById(normalizedRecordId);
            if (latest == null) {
                log.warn("event=personal_record_not_found recordId={}", normalizedRecordId);
                throw notFound();
            }
            log.warn("event=personal_record_update_conflict recordId={} requestedVersion={} currentVersion={}",
                    normalizedRecordId, record.getVersion(), latest.getVersion());
            throw versionConflict();
        }

        PersonalRecordDO updated = findActiveRecord(normalizedRecordId);
        log.info("event=personal_record_updated recordId={} recordType={} versionBefore={} versionAfter={} "
                        + "contentLength={} recallEnabled={} ragEnabled={}",
                updated.getRecordId(), updated.getRecordType(), current.getVersion(), updated.getVersion(),
                codePointLength(updated.getContent()), updated.getRecallEnabled(), updated.getRagEnabled());
        return updated;
    }

    @Override
    @Transactional
    public void deleteRecord(Long recordId, Integer version) {
        long normalizedRecordId = validateRecordId(recordId);
        int normalizedVersion = validateVersion(version);
        PersonalRecordDO current = findActiveRecord(normalizedRecordId);

        int deletedRows = personalRecordDao.logicalDelete(normalizedRecordId, normalizedVersion);
        if (deletedRows == 0) {
            PersonalRecordDO latest = personalRecordDao.selectActiveById(normalizedRecordId);
            if (latest == null) {
                log.warn("event=personal_record_not_found recordId={}", normalizedRecordId);
                throw notFound();
            }
            log.warn("event=personal_record_delete_conflict recordId={} requestedVersion={} currentVersion={}",
                    normalizedRecordId, normalizedVersion, latest.getVersion());
            throw versionConflict();
        }

        log.info("event=personal_record_deleted recordId={} recordType={} versionBefore={}",
                normalizedRecordId, current.getRecordType(), current.getVersion());
    }

    @Override
    public List<PersonalRecordRecall> listRecalls(Integer limit) {
        int normalizedLimit = validateRecallLimit(limit);
        return personalRecordDao.selectActivePage(null, true, 0, normalizedLimit).stream()
                .map(record -> new PersonalRecordRecall(
                        record.getRecordId(),
                        record.getRecordType(),
                        record.getTitle(),
                        createExcerpt(record.getContent()),
                        record.getMood(),
                        record.getRecordDate()))
                .toList();
    }

    private void validateAndNormalizeRecord(PersonalRecordDO record, boolean requireVersion) {
        if (record == null) {
            throw badRequest("Request body is required.");
        }

        record.setRecordType(normalizeRequiredRecordType(record.getRecordType()));
        record.setTitle(normalizeOptionalText(record.getTitle()));
        if (record.getTitle() != null && codePointLength(record.getTitle()) > MAX_TITLE_LENGTH) {
            throw badRequest("Record title must not exceed 120 characters.");
        }

        if (record.getContent() == null || record.getContent().trim().isEmpty()) {
            throw badRequest("Record content is required.");
        }
        if (codePointLength(record.getContent()) > MAX_CONTENT_LENGTH) {
            throw badRequest("Record content must not exceed 100000 characters.");
        }
        if (record.getRecordDate() == null) {
            throw badRequest("Record date is required.");
        }

        record.setMood(normalizeOptionalText(record.getMood()));
        if (record.getMood() != null && codePointLength(record.getMood()) > MAX_MOOD_LENGTH) {
            throw badRequest("Record mood must not exceed 32 characters.");
        }
        record.setRecallEnabled(Boolean.TRUE.equals(record.getRecallEnabled()));
        record.setRagEnabled(Boolean.TRUE.equals(record.getRagEnabled()));

        if (requireVersion) {
            record.setVersion(validateVersion(record.getVersion()));
        }
    }

    private String normalizeRequiredRecordType(String recordType) {
        if (recordType == null || recordType.trim().isEmpty()) {
            throw badRequest("Record type is required.");
        }
        String normalizedRecordType = recordType.trim().toUpperCase(Locale.ROOT);
        if (!RECORD_TYPES.contains(normalizedRecordType)) {
            throw badRequest("Record type must be DIARY, THOUGHT, or WORK_NOTE.");
        }
        return normalizedRecordType;
    }

    private String normalizeOptionalRecordType(String recordType) {
        if (recordType == null || recordType.trim().isEmpty()) {
            return null;
        }
        return normalizeRequiredRecordType(recordType);
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private long validateRecordId(Long recordId) {
        if (recordId == null) {
            throw badRequest("Record id is required.");
        }
        if (recordId <= 0) {
            throw badRequest("Record id must be positive.");
        }
        return recordId;
    }

    private int validateVersion(Integer version) {
        if (version == null) {
            throw badRequest("Record version is required.");
        }
        if (version < 0) {
            throw badRequest("Record version must not be negative.");
        }
        return version;
    }

    private int validatePage(Integer page) {
        if (page == null || page < 1) {
            throw badRequest("Page must be at least 1.");
        }
        return page;
    }

    private int validatePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw badRequest("Page size must be between 1 and 100.");
        }
        return pageSize;
    }

    private int validateRecallLimit(Integer limit) {
        if (limit == null || limit < 1 || limit > MAX_RECALL_LIMIT) {
            throw badRequest("Recall limit must be between 1 and 50.");
        }
        return limit;
    }

    private PersonalRecordDO findActiveRecord(long recordId) {
        PersonalRecordDO record = personalRecordDao.selectActiveById(recordId);
        if (record != null) {
            return record;
        }
        log.warn("event=personal_record_not_found recordId={}", recordId);
        throw notFound();
    }

    private String createExcerpt(String content) {
        String normalized = content.strip().replaceAll("\\s+", " ");
        int length = codePointLength(normalized);
        if (length <= EXCERPT_LENGTH) {
            return normalized;
        }
        int endIndex = normalized.offsetByCodePoints(0, EXCERPT_LENGTH);
        return normalized.substring(0, endIndex) + "…";
    }

    private int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Personal record was not found.");
    }

    private ResponseStatusException versionConflict() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT, "Personal record has been updated. Refresh and try again.");
    }
}
