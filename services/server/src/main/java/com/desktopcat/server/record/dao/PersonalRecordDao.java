package com.desktopcat.server.record.dao;

import com.desktopcat.server.record.dao.dataobject.PersonalRecordDO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PersonalRecordDao {
    int insertRecord(PersonalRecordDO record);

    PersonalRecordDO selectActiveById(@Param("recordId") long recordId);

    List<PersonalRecordDO> selectActivePage(
            @Param("recordType") String recordType,
            @Param("recallEnabled") Boolean recallEnabled,
            @Param("offset") int offset,
            @Param("limit") int limit);

    long countActive(
            @Param("recordType") String recordType,
            @Param("recallEnabled") Boolean recallEnabled);

    int updateRecord(PersonalRecordDO record);

    int logicalDelete(
            @Param("recordId") long recordId,
            @Param("version") int version);
}
