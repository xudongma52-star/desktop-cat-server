package com.desktopcat.server.record.service;

import com.desktopcat.server.record.dto.PersonalRecordCreateDto;
import com.desktopcat.server.record.dto.PersonalRecordDetailDto;
import com.desktopcat.server.record.dto.PersonalRecordPageDto;
import com.desktopcat.server.record.dto.PersonalRecordRecallDto;
import com.desktopcat.server.record.dto.PersonalRecordUpdateDto;
import java.util.List;

public interface PersonalRecordService {
    PersonalRecordDetailDto createRecord(PersonalRecordCreateDto request);

    PersonalRecordPageDto listRecords(Integer page, Integer pageSize, String recordType);

    PersonalRecordDetailDto getRecord(Long recordId);

    PersonalRecordDetailDto updateRecord(Long recordId, PersonalRecordUpdateDto request);

    void deleteRecord(Long recordId, Integer version);

    List<PersonalRecordRecallDto> listRecalls(Integer limit);
}
