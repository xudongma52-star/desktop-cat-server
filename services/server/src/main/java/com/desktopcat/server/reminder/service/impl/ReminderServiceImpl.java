package com.desktopcat.server.reminder.service.impl;

import com.desktopcat.server.reminder.dao.ReminderDO;
import com.desktopcat.server.reminder.dao.ReminderDao;
import com.desktopcat.server.reminder.dto.ReminderCompleteDto;
import com.desktopcat.server.reminder.dto.ReminderCreateDto;
import com.desktopcat.server.reminder.dto.ReminderDto;
import com.desktopcat.server.reminder.dto.ReminderUpdateDto;
import com.desktopcat.server.reminder.service.ReminderService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

/** 待办与一次性提醒的业务层实现。 */
@Service
@Profile("postgres")
public class ReminderServiceImpl implements ReminderService {
    private static final Logger log = LoggerFactory.getLogger(ReminderServiceImpl.class);
    private static final ZoneId USER_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> SCOPES = Set.of("TODAY", "PENDING");
    private static final int MAX_CONTENT_LENGTH = 200;
    private static final String PENDING = "PENDING";

    private final ReminderDao reminderDao;

    public ReminderServiceImpl(ReminderDao reminderDao) {
        this.reminderDao = reminderDao;
    }

    @Override
    @Transactional
    public ReminderDto create(ReminderCreateDto request) {
        if (request == null) {
            throw badRequest("Request body is required.");
        }
        String content = validateContent(request.content());
        Instant remindAt = validateRemindAt(request.remindAt());
        ReminderDO reminder = new ReminderDO(
                null, content, remindAt, PENDING, null, 0, null, null);

        int insertedRows = reminderDao.insertReminder(reminder);
        if (insertedRows != 1 || reminder.getReminderId() == null) {
            log.error("event=reminder_create_failed remindAt={} contentLength={}",
                    remindAt, codePointLength(content));
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Reminder could not be created.");
        }

        ReminderDO created = findActiveReminder(reminder.getReminderId());
        log.info("event=reminder_created reminderId={} remindAt={} status={} contentLength={}",
                created.getReminderId(), created.getRemindAt(), created.getStatus(),
                codePointLength(created.getContent()));
        return toDto(created);
    }

    @Override
    public List<ReminderDto> list(String scope) {
        String normalizedScope = normalizeScope(scope);
        String status = null;
        Instant startTime = null;
        Instant endTime = null;
        if ("PENDING".equals(normalizedScope)) {
            status = PENDING;
        } else {
            LocalDate today = LocalDate.now(USER_ZONE);
            startTime = today.atStartOfDay(USER_ZONE).toInstant();
            endTime = today.plusDays(1).atStartOfDay(USER_ZONE).toInstant();
        }
        return reminderDao.selectActiveList(status, startTime, endTime).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public ReminderDto update(Long reminderId, ReminderUpdateDto request) {
        long normalizedId = validateReminderId(reminderId);
        if (request == null) {
            throw badRequest("Request body is required.");
        }
        int version = validateVersion(request.version());
        ReminderDO current = findActiveReminder(normalizedId);
        ensurePending(current);

        ReminderDO update = new ReminderDO(
                normalizedId,
                validateContent(request.content()),
                validateRemindAt(request.remindAt()),
                PENDING,
                null,
                version,
                null,
                null);
        int updatedRows = reminderDao.updateReminder(update);
        if (updatedRows != 1) {
            resolveWriteFailure(normalizedId, version, true);
        }

        ReminderDO updated = findActiveReminder(normalizedId);
        log.info("event=reminder_updated reminderId={} remindAt={} versionBefore={} versionAfter={} "
                        + "contentLength={}",
                updated.getReminderId(), updated.getRemindAt(), version, updated.getVersion(),
                codePointLength(updated.getContent()));
        return toDto(updated);
    }

    @Override
    @Transactional
    public ReminderDto complete(Long reminderId, ReminderCompleteDto request) {
        long normalizedId = validateReminderId(reminderId);
        if (request == null) {
            throw badRequest("Request body is required.");
        }
        int version = validateVersion(request.version());
        ReminderDO current = findActiveReminder(normalizedId);
        ensurePending(current);

        int updatedRows = reminderDao.completeReminder(normalizedId, version);
        if (updatedRows != 1) {
            resolveWriteFailure(normalizedId, version, true);
        }

        ReminderDO completed = findActiveReminder(normalizedId);
        log.info("event=reminder_completed reminderId={} remindAt={} versionBefore={} versionAfter={}",
                completed.getReminderId(), completed.getRemindAt(), version, completed.getVersion());
        return toDto(completed);
    }

    @Override
    @Transactional
    public void delete(Long reminderId, Integer version) {
        long normalizedId = validateReminderId(reminderId);
        int normalizedVersion = validateVersion(version);
        findActiveReminder(normalizedId);

        int updatedRows = reminderDao.logicalDelete(normalizedId, normalizedVersion);
        if (updatedRows != 1) {
            resolveWriteFailure(normalizedId, normalizedVersion, false);
        }
        log.info("event=reminder_deleted reminderId={} version={}", normalizedId, normalizedVersion);
    }

    private void resolveWriteFailure(long reminderId, int expectedVersion, boolean requirePending) {
        ReminderDO latest = reminderDao.selectActiveById(reminderId);
        if (latest == null) {
            throw notFound();
        }
        if (latest.getVersion() != expectedVersion) {
            log.warn("event=reminder_update_conflict reminderId={} requestedVersion={} currentVersion={}",
                    reminderId, expectedVersion, latest.getVersion());
            throw versionConflict();
        }
        if (requirePending && !PENDING.equals(latest.getStatus())) {
            throw badRequest("Only pending reminders can be changed.");
        }
        throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR, "Reminder could not be updated.");
    }

    private ReminderDO findActiveReminder(long reminderId) {
        ReminderDO reminder = reminderDao.selectActiveById(reminderId);
        if (reminder != null) {
            return reminder;
        }
        log.warn("event=reminder_not_found reminderId={}", reminderId);
        throw notFound();
    }

    private void ensurePending(ReminderDO reminder) {
        if (!PENDING.equals(reminder.getStatus())) {
            throw badRequest("Only pending reminders can be changed.");
        }
    }

    private String normalizeScope(String scope) {
        String normalized = scope == null || scope.isBlank()
                ? "TODAY"
                : scope.trim().toUpperCase(Locale.ROOT);
        if (!SCOPES.contains(normalized)) {
            throw badRequest("Reminder scope must be TODAY or PENDING.");
        }
        return normalized;
    }

    private String validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw badRequest("Reminder content is required.");
        }
        String normalized = content.strip();
        if (codePointLength(normalized) > MAX_CONTENT_LENGTH) {
            throw badRequest("Reminder content must not exceed 200 characters.");
        }
        return normalized;
    }

    private Instant validateRemindAt(Instant remindAt) {
        if (remindAt == null) {
            throw badRequest("Reminder time is required.");
        }
        return remindAt;
    }

    private long validateReminderId(Long reminderId) {
        if (reminderId == null) {
            throw badRequest("Reminder id is required.");
        }
        if (reminderId <= 0) {
            throw badRequest("Reminder id must be positive.");
        }
        return reminderId;
    }

    private int validateVersion(Integer version) {
        if (version == null) {
            throw badRequest("Reminder version is required.");
        }
        if (version < 0) {
            throw badRequest("Reminder version must not be negative.");
        }
        return version;
    }

    private ReminderDto toDto(ReminderDO reminder) {
        return new ReminderDto(
                reminder.getReminderId(), reminder.getContent(), reminder.getRemindAt(),
                reminder.getStatus(), reminder.getCompletedAt(), reminder.getVersion(),
                reminder.getCreatedAt(), reminder.getUpdatedAt());
    }

    private int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Reminder was not found.");
    }

    private ResponseStatusException versionConflict() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT, "Reminder has been updated. Refresh and try again.");
    }
}
