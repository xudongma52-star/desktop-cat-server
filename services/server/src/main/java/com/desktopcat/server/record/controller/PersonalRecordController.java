package com.desktopcat.server.record.controller;

import com.desktopcat.server.record.dto.PersonalRecordActivityDto;
import com.desktopcat.server.record.dto.PersonalRecordCreateDto;
import com.desktopcat.server.record.dto.PersonalRecordDetailDto;
import com.desktopcat.server.record.dto.PersonalRecordPageDto;
import com.desktopcat.server.record.dto.PersonalRecordRecallDto;
import com.desktopcat.server.record.dto.PersonalRecordUpdateDto;
import com.desktopcat.server.record.service.PersonalRecordService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人文章 HTTP 接口。
 *
 * <p>所有路径都以 {@code /api/records} 开头。Controller 只读取 HTTP 参数并调用
 * Service，不直接校验业务规则，也不访问 DAO 或数据库。</p>
 */
@RestController
// 文章功能依赖 PostgreSQL；使用 local 配置时不注册这个 Controller。
@Profile("postgres")
@RequestMapping("/api/records")
public class PersonalRecordController {
    // Controller 只依赖 Service 接口，不依赖具体实现类和 DAO。
    private final PersonalRecordService personalRecordService;

    /** Spring 通过构造器注入 PersonalRecordService 的实现。 */
    public PersonalRecordController(PersonalRecordService personalRecordService) {
        this.personalRecordService = personalRecordService;
    }

    /**
     * POST /api/records：创建文章。
     *
     * @param request 前端提交的文章；请求体缺失时传 null，由 Service 返回统一参数错误
     * @return 数据库保存后的完整文章，包含主键、版本和审计时间
     */
    @PostMapping
    // 创建成功使用 201 Created，而不是普通查询使用的 200 OK。
    @ResponseStatus(HttpStatus.CREATED)
    public PersonalRecordDetailDto createRecord(
            @RequestBody(required = false) PersonalRecordCreateDto request) {
        return personalRecordService.createRecord(request);
    }

    /**
     * GET /api/records：分页查询未删除文章，可按文章类型筛选。
     *
     * @param page 页码，默认从第 1 页开始
     * @param pageSize 每页数量，默认 12
     * @param recordType 可选类型：DIARY、THOUGHT 或 WORK_NOTE；不传表示全部
     * @return 文章摘要列表和分页统计
     */
    @GetMapping
    public PersonalRecordPageDto listRecords(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "12") Integer pageSize,
            @RequestParam(required = false) String recordType) {
        return personalRecordService.listRecords(page, pageSize, recordType);
    }

    /**
     * GET /api/records/activity：按天统计指定日期范围内的写作数量。
     *
     * @param startDate 统计开始日期，包含当天
     * @param endDate 统计结束日期，包含当天
     * @param recordType 文章类型，默认只统计日记
     * @return 有写作记录的日期、每天篇数及范围汇总
     */
    @GetMapping("/activity")
    public PersonalRecordActivityDto getActivity(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "DIARY") String recordType) {
        return personalRecordService.getActivity(startDate, endDate, recordType);
    }

    /**
     * GET /api/records/{recordId}：查询一篇文章的完整内容。
     *
     * @param recordId 路径中的文章主键，例如 /api/records/1 中的 1
     * @return 未删除文章的完整信息
     */
    @GetMapping("/{recordId}")
    public PersonalRecordDetailDto getRecord(@PathVariable Long recordId) {
        return personalRecordService.getRecord(recordId);
    }

    /**
     * PUT /api/records/{recordId}：完整修改文章。
     *
     * @param recordId 路径中的文章主键
     * @param request 修改内容，其中 version 用于乐观锁，防止覆盖其他客户端的新修改
     * @return 修改后且版本号已自增的完整文章
     */
    @PutMapping("/{recordId}")
    public PersonalRecordDetailDto updateRecord(
            @PathVariable Long recordId,
            @RequestBody(required = false) PersonalRecordUpdateDto request) {
        return personalRecordService.updateRecord(recordId, request);
    }

    /**
     * DELETE /api/records/{recordId}?version=...：按版本逻辑删除文章。
     *
     * @param recordId 路径中的文章主键
     * @param version 客户端最后读取到的版本号，用于乐观锁
     */
    @DeleteMapping("/{recordId}")
    // 删除成功只返回 204 状态，不返回 JSON 响应体。
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRecord(
            @PathVariable Long recordId,
            @RequestParam(required = false) Integer version) {
        personalRecordService.deleteRecord(recordId, version);
    }

    /**
     * GET /api/records/recalls：查询允许在首页轮播的文字回忆。
     *
     * @param limit 最多返回多少条，默认 10
     * @return 仅包含轮播所需字段的回忆 DTO 列表
     */
    @GetMapping("/recalls")
    public List<PersonalRecordRecallDto> listRecalls(
            @RequestParam(defaultValue = "10") Integer limit) {
        return personalRecordService.listRecalls(limit);
    }
}
