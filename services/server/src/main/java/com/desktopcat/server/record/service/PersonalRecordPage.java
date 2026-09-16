package com.desktopcat.server.record.service;

import com.desktopcat.server.record.dao.PersonalRecordDO;
import java.util.List;

public record PersonalRecordPage(
        List<PersonalRecordDO> items,
        int page,
        int pageSize,
        long total,
        long totalPages) {
}
