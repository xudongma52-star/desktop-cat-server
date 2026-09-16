package com.desktopcat.server.record.service;

import com.desktopcat.server.record.dao.PersonalRecordDO;
import java.util.List;

public interface PersonalRecordService {
    PersonalRecordDO createRecord(PersonalRecordDO record);

    PersonalRecordPage listRecords(Integer page, Integer pageSize, String recordType);

    PersonalRecordDO getRecord(Long recordId);

    PersonalRecordDO updateRecord(Long recordId, PersonalRecordDO record);

    void deleteRecord(Long recordId, Integer version);

    List<PersonalRecordRecall> listRecalls(Integer limit);
}
