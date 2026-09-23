package com.desktopcat.server.record.service;

import com.desktopcat.server.record.dto.PersonalRecordActivityDto;
import com.desktopcat.server.record.dto.PersonalRecordCreateDto;
import com.desktopcat.server.record.dto.PersonalRecordDetailDto;
import com.desktopcat.server.record.dto.PersonalRecordPageDto;
import com.desktopcat.server.record.dto.PersonalRecordRecallDto;
import com.desktopcat.server.record.dto.PersonalRecordUpdateDto;
import java.time.LocalDate;
import java.util.List;

public interface PersonalRecordService {
    PersonalRecordDetailDto createRecord(long userId, PersonalRecordCreateDto request);

    PersonalRecordPageDto listRecords(long userId, Integer page, Integer pageSize, String recordType);

    PersonalRecordActivityDto getActivity(
            long userId, LocalDate startDate, LocalDate endDate, String recordType);

    PersonalRecordDetailDto getRecord(long userId, Long recordId);

    PersonalRecordDetailDto updateRecord(
            long userId, Long recordId, PersonalRecordUpdateDto request);

    void deleteRecord(long userId, Long recordId, Integer version);

    List<PersonalRecordRecallDto> listRecalls(long userId, Integer limit);
}
