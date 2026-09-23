package com.desktopcat.server.reminder.service;

import com.desktopcat.server.reminder.dto.ReminderCompleteDto;
import com.desktopcat.server.reminder.dto.ReminderCreateDto;
import com.desktopcat.server.reminder.dto.ReminderDto;
import com.desktopcat.server.reminder.dto.ReminderUpdateDto;
import java.util.List;

public interface ReminderService {
    ReminderDto create(ReminderCreateDto request);

    List<ReminderDto> list(String scope);

    ReminderDto update(Long reminderId, ReminderUpdateDto request);

    ReminderDto complete(Long reminderId, ReminderCompleteDto request);

    void delete(Long reminderId, Integer version);
}
