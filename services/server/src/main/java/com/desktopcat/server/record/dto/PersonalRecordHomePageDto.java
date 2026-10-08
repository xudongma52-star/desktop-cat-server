package com.desktopcat.server.record.dto;

import java.util.List;

/** 首页图文列表的分页结果，每次加载十篇。 */
public record PersonalRecordHomePageDto(
        List<PersonalRecordHomeItemDto> items, int page, boolean hasMore) {
}
