package com.desktopcat.server.record.service.impl;

import com.desktopcat.server.record.dao.PersonalRecordActivityDO;
import com.desktopcat.server.record.dao.PersonalRecordDao;
import com.desktopcat.server.record.dao.PersonalRecordDO;
import com.desktopcat.server.record.dto.PersonalRecordActivityDayDto;
import com.desktopcat.server.record.dto.PersonalRecordActivityDto;
import com.desktopcat.server.record.dto.PersonalRecordCreateDto;
import com.desktopcat.server.record.dto.PersonalRecordDetailDto;
import com.desktopcat.server.record.dto.PersonalRecordListItemDto;
import com.desktopcat.server.record.dto.PersonalRecordPageDto;
import com.desktopcat.server.record.dto.PersonalRecordRecallDto;
import com.desktopcat.server.record.dto.PersonalRecordUpdateDto;
import com.desktopcat.server.record.service.PersonalRecordService;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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

/**
 * 个人文章模块的业务层实现。
 *
 * <p>Controller 只负责接收和返回 HTTP 数据；本类集中处理参数校验、数据规范化、
 * 分页、乐观锁和事务；真正的数据库读写交给 {@link PersonalRecordDao}。</p>
 *
 * <p>该实现只在 {@code postgres} 配置启用时注册，避免无数据库的 {@code local}
 * 配置误调用 MyBatis。</p>
 */
@Service
@Profile("postgres")
public class PersonalRecordServiceImpl implements PersonalRecordService {
    private static final Logger log = LoggerFactory.getLogger(PersonalRecordServiceImpl.class);

    // 业务规则统一放在常量中，避免创建、修改和查询使用不同的限制。
    private static final Set<String> RECORD_TYPES = Set.of("DIARY", "THOUGHT", "WORK_NOTE");
    private static final int MAX_TITLE_LENGTH = 120;
    private static final int MAX_CONTENT_LENGTH = 100_000;
    private static final int MAX_MOOD_LENGTH = 32;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_RECALL_LIMIT = 50;
    private static final int EXCERPT_LENGTH = 160;
    private static final int MAX_ACTIVITY_RANGE_DAYS = 366;

    private final PersonalRecordDao personalRecordDao;

    // 使用构造器注入，使依赖关系明确，也方便在测试中传入替代实现。
    public PersonalRecordServiceImpl(PersonalRecordDao personalRecordDao) {
        this.personalRecordDao = personalRecordDao;
    }

    /**
     * 创建文章。事务保证插入和随后重新读取属于同一次业务操作。
    */
    @Override
    @Transactional
    public PersonalRecordDetailDto createRecord(long userId, PersonalRecordCreateDto request) {
        // 创建时不接收客户端版本号；主键和初始版本完全由服务端控制。
        PersonalRecordDO record = validateAndNormalizeRecord(toDataObject(request), false);
        record.setRecordId(null);
        record.setUserId(userId);
        record.setVersion(0);

        int insertedRows = personalRecordDao.insertRecord(record);
        // MyBatis 插入成功后会把数据库生成的 record_id 回填到 record。
        if (insertedRows != 1 || record.getRecordId() == null) {
            log.error("event=personal_record_create_failed recordType={} contentLength={} recallEnabled={} ragEnabled={}",
                    record.getRecordType(), codePointLength(record.getContent()),
                    record.getRecallEnabled(), record.getRagEnabled());
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Personal record could not be created.");
        }

        // 重新查询一次，保证返回数据库最终保存的默认值和审计时间。
        PersonalRecordDO created = findActiveRecord(userId, record.getRecordId());
        log.info("event=personal_record_created userId={} recordId={} recordType={} version={} contentLength={} "
                        + "recallEnabled={} ragEnabled={}",
                userId, created.getRecordId(), created.getRecordType(), created.getVersion(),
                codePointLength(created.getContent()), created.getRecallEnabled(), created.getRagEnabled());
        return toDetailDto(created);
    }

    /**
     * 分页查询未删除文章，可按文章类型筛选。
     */
    @Override
    public PersonalRecordPageDto listRecords(
            long userId, Integer page, Integer pageSize, String recordType) {
        int normalizedPage = validatePage(page);
        int normalizedPageSize = validatePageSize(pageSize);
        String normalizedRecordType = normalizeOptionalRecordType(recordType);

        // 先转换为 long 再计算，避免 page 很大时发生 int 乘法溢出。
        long offsetValue = (long) (normalizedPage - 1) * normalizedPageSize;
        // DAO 的 offset 参数为 int，超出范围时直接返回明确的参数错误。
        if (offsetValue > Integer.MAX_VALUE) {
            throw badRequest("Requested page is too large.");
        }

        // 计数和分页查询必须使用相同筛选条件，否则 total 会与 items 不一致。
        long total = personalRecordDao.countActive(userId, normalizedRecordType, null);
        List<PersonalRecordListItemDto> items = personalRecordDao.selectActivePage(
                        userId, normalizedRecordType, null,
                        (int) offsetValue, normalizedPageSize).stream()
                .map(this::toListItem)
                .toList();

        // 整数公式实现向上取整；没有数据时约定总页数为 0。
        long totalPages = total == 0 ? 0 : (total + normalizedPageSize - 1) / normalizedPageSize;
        // List.copyOf 防止调用方增删分页结果中的元素。
        return new PersonalRecordPageDto(List.copyOf(items), normalizedPage,
                normalizedPageSize, total, totalPages);
    }

    /**
     * 查询一段日期范围内每天写了多少篇文章。数据库只返回有记录的日期，
     * 没有写作的日期由前端绘制为空白格子。
     */
    @Override
    public PersonalRecordActivityDto getActivity(
            long userId, LocalDate startDate, LocalDate endDate, String recordType) {
        if (startDate == null) {
            throw badRequest("Activity start date is required.");
        }
        if (endDate == null) {
            throw badRequest("Activity end date is required.");
        }
        if (startDate.isAfter(endDate)) {
            throw badRequest("Activity start date must not be after end date.");
        }

        long rangeDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (rangeDays > MAX_ACTIVITY_RANGE_DAYS) {
            throw badRequest("Activity date range must not exceed 366 days.");
        }

        String normalizedRecordType = normalizeRequiredRecordType(recordType);
        List<PersonalRecordActivityDayDto> days = personalRecordDao
                .selectDailyActivity(userId, normalizedRecordType, startDate, endDate).stream()
                .map(this::toActivityDayDto)
                .toList();
        long totalRecords = days.stream()
                .mapToLong(PersonalRecordActivityDayDto::recordCount)
                .sum();
        return new PersonalRecordActivityDto(
                startDate, endDate, totalRecords, days.size(), List.copyOf(days));
    }

    /**
     * 按主键查询一篇未删除文章。
    */
    @Override
    public PersonalRecordDetailDto getRecord(long userId, Long recordId) {
        return toDetailDto(findActiveRecord(userId, validateRecordId(recordId)));
    }

    /**
     * 完整更新文章。SQL 同时匹配 record_id、version 和未删除状态，避免并发覆盖。
    */
    @Override
    @Transactional
    public PersonalRecordDetailDto updateRecord(
            long userId, Long recordId, PersonalRecordUpdateDto request) {
        long normalizedRecordId = validateRecordId(recordId);
        // 修改操作必须携带客户端最近一次读取到的 version。
        PersonalRecordDO record = validateAndNormalizeRecord(toDataObject(request), true);
        PersonalRecordDO current = findActiveRecord(userId, normalizedRecordId);
        record.setRecordId(normalizedRecordId);
        record.setUserId(userId);

        int updatedRows = personalRecordDao.updateRecord(record);
        if (updatedRows == 0) {
            // 更新行数为 0 可能是数据已删除，也可能是 version 已变化，需要分别提示。
            PersonalRecordDO latest = personalRecordDao.selectActiveById(userId, normalizedRecordId);
            if (latest == null) {
                log.warn("event=personal_record_not_found recordId={}", normalizedRecordId);
                throw notFound();
            }
            log.warn("event=personal_record_update_conflict recordId={} requestedVersion={} currentVersion={}",
                    normalizedRecordId, record.getVersion(), latest.getVersion());
            throw versionConflict();
        }

        // DAO 会让 version 自增，因此重新读取后再把最新版本返回给客户端。
        PersonalRecordDO updated = findActiveRecord(userId, normalizedRecordId);
        log.info("event=personal_record_updated userId={} recordId={} recordType={} versionBefore={} versionAfter={} "
                        + "contentLength={} recallEnabled={} ragEnabled={}",
                userId, updated.getRecordId(), updated.getRecordType(),
                current.getVersion(), updated.getVersion(),
                codePointLength(updated.getContent()), updated.getRecallEnabled(), updated.getRagEnabled());
        return toDetailDto(updated);
    }

    /**
     * 按版本逻辑删除文章。数据库只填写 deleted_at，不物理移除历史数据。
     */
    @Override
    @Transactional
    public void deleteRecord(long userId, Long recordId, Integer version) {
        long normalizedRecordId = validateRecordId(recordId);
        int normalizedVersion = validateVersion(version);
        PersonalRecordDO current = findActiveRecord(userId, normalizedRecordId);

        int deletedRows = personalRecordDao.logicalDelete(userId, normalizedRecordId, normalizedVersion);
        if (deletedRows == 0) {
            // 与更新相同，区分“文章不存在”和“版本冲突”两种失败原因。
            PersonalRecordDO latest = personalRecordDao.selectActiveById(userId, normalizedRecordId);
            if (latest == null) {
                log.warn("event=personal_record_not_found recordId={}", normalizedRecordId);
                throw notFound();
            }
            log.warn("event=personal_record_delete_conflict recordId={} requestedVersion={} currentVersion={}",
                    normalizedRecordId, normalizedVersion, latest.getVersion());
            throw versionConflict();
        }

        log.info("event=personal_record_deleted userId={} recordId={} recordType={} versionBefore={}",
                userId, normalizedRecordId, current.getRecordType(), current.getVersion());
    }

    /**
     * 查询允许进入温馨回忆轮播的文章，并转换为轮播需要的精简数据。
     */
    @Override
    public List<PersonalRecordRecallDto> listRecalls(long userId, Integer limit) {
        int normalizedLimit = validateRecallLimit(limit);
        // 复用通用分页查询：不限制文章类型，只筛选 recall_enabled = true。
        return personalRecordDao.selectActivePage(
                        userId, null, true, 0, normalizedLimit).stream()
                .map(record -> new PersonalRecordRecallDto(
                        record.getRecordId(),
                        record.getRecordType(),
                        record.getTitle(),
                        createExcerpt(record.getContent()),
                        record.getMood(),
                        record.getRecordDate()))
                .toList();
    }

    /**
     * 校验 Controller 传入的数据，并把规范化结果写入 DAO 数据对象。
     *
     * @param requireVersion 创建时为 false，更新时为 true
     */
    private PersonalRecordDO validateAndNormalizeRecord(
            PersonalRecordDO record, boolean requireVersion) {
        if (record == null) {
            throw badRequest("Request body is required.");
        }

        String recordType = normalizeRequiredRecordType(record.getRecordType());
        String title = normalizeOptionalText(record.getTitle());
        if (title != null && codePointLength(title) > MAX_TITLE_LENGTH) {
            throw badRequest("Record title must not exceed 120 characters.");
        }

        String content = record.getContent();
        if (content == null || content.trim().isEmpty()) {
            throw badRequest("Record content is required.");
        }
        if (codePointLength(content) > MAX_CONTENT_LENGTH) {
            throw badRequest("Record content must not exceed 100000 characters.");
        }
        if (record.getRecordDate() == null) {
            throw badRequest("Record date is required.");
        }

        String mood = normalizeOptionalText(record.getMood());
        if (mood != null && codePointLength(mood) > MAX_MOOD_LENGTH) {
            throw badRequest("Record mood must not exceed 32 characters.");
        }

        // Boolean.TRUE.equals 同时处理 null：前端未传开关时统一按 false 保存。
        record.setRecordType(recordType);
        record.setTitle(title);
        record.setMood(mood);
        record.setRecallEnabled(Boolean.TRUE.equals(record.getRecallEnabled()));
        record.setRagEnabled(Boolean.TRUE.equals(record.getRagEnabled()));
        record.setVersion(requireVersion ? validateVersion(record.getVersion()) : null);
        return record;
    }

    /** 将创建 DTO 转换为 DAO 数据对象；主键、版本和审计时间稍后由服务端填写。 */
    private PersonalRecordDO toDataObject(PersonalRecordCreateDto request) {
        if (request == null) {
            return null;
        }
        return new PersonalRecordDO(
                null, null, request.recordType(), request.title(), request.content(),
                request.recordDate(), request.mood(), request.recallEnabled(), request.ragEnabled(),
                null, null, null);
    }

    /** 将修改 DTO 转换为 DAO 数据对象，并保留客户端提交的乐观锁版本。 */
    private PersonalRecordDO toDataObject(PersonalRecordUpdateDto request) {
        if (request == null) {
            return null;
        }
        return new PersonalRecordDO(
                null, null, request.recordType(), request.title(), request.content(),
                request.recordDate(), request.mood(), request.recallEnabled(), request.ragEnabled(),
                request.version(), null, null);
    }

    /** DAO 数据对象转换为创建、详情和修改接口共用的完整文章 DTO。 */
    private PersonalRecordDetailDto toDetailDto(PersonalRecordDO record) {
        return new PersonalRecordDetailDto(
                record.getRecordId(), record.getRecordType(), record.getTitle(), record.getContent(),
                record.getRecordDate(), record.getMood(),
                Boolean.TRUE.equals(record.getRecallEnabled()), Boolean.TRUE.equals(record.getRagEnabled()),
                record.getVersion(), record.getCreatedAt(), record.getUpdatedAt());
    }

    /** DAO 的分页摘要转换为含义明确的 Service 列表项。 */
    private PersonalRecordListItemDto toListItem(PersonalRecordDO record) {
        return new PersonalRecordListItemDto(
                record.getRecordId(), record.getRecordType(), record.getTitle(),
                createExcerpt(record.getContent()), record.getRecordDate(), record.getMood(),
                Boolean.TRUE.equals(record.getRecallEnabled()), Boolean.TRUE.equals(record.getRagEnabled()),
                record.getVersion(), record.getCreatedAt(), record.getUpdatedAt());
    }

    /** DAO 聚合结果转换为写作足迹中的单日数据。 */
    private PersonalRecordActivityDayDto toActivityDayDto(PersonalRecordActivityDO activity) {
        return new PersonalRecordActivityDayDto(
                activity.getRecordDate(), activity.getRecordCount());
    }

    /**
     * 规范化必填文章类型。Locale.ROOT 可避免受操作系统语言规则影响。
     */
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

    /**
     * 查询条件中的空文章类型表示“不按类型筛选”。
     */
    private String normalizeOptionalRecordType(String recordType) {
        if (recordType == null || recordType.trim().isEmpty()) {
            return null;
        }
        return normalizeRequiredRecordType(recordType);
    }

    /**
     * 可选文本统一去除首尾空格；只有空白时转换为 null。
     */
    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    // 以下校验方法各自对应一个具体错误，便于前端准确展示失败原因。
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

    /**
     * 集中查询未删除文章，并把“查不到”统一转换为 404。
     */
    private PersonalRecordDO findActiveRecord(long userId, long recordId) {
        PersonalRecordDO record = personalRecordDao.selectActiveById(userId, recordId);
        if (record != null) {
            return record;
        }
        log.warn("event=personal_record_not_found userId={} recordId={}", userId, recordId);
        throw notFound();
    }

    /**
     * 生成列表和轮播摘要：合并换行及连续空白，最多保留 160 个 Unicode 字符。
     */
    private String createExcerpt(String content) {
        String normalized = content.strip().replaceAll("\\s+", " ");
        int length = codePointLength(normalized);
        if (length <= EXCERPT_LENGTH) {
            return normalized;
        }
        int endIndex = normalized.offsetByCodePoints(0, EXCERPT_LENGTH);
        return normalized.substring(0, endIndex) + "…";
    }

    /**
     * 按 Unicode 码点计数，使常见 emoji 不会像 String.length() 那样被算作两个 char。
     */
    private int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    // 复用 Spring 的 ResponseStatusException，由全局异常处理器转换成统一错误响应。
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
