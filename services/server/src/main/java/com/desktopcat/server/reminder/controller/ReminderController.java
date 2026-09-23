package com.desktopcat.server.reminder.controller;

import com.desktopcat.server.reminder.dto.ReminderCompleteDto;
import com.desktopcat.server.reminder.dto.ReminderCreateDto;
import com.desktopcat.server.reminder.dto.ReminderDto;
import com.desktopcat.server.reminder.dto.ReminderUpdateDto;
import com.desktopcat.server.reminder.service.ReminderService;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("postgres")
@RequestMapping("/api/reminders")
public class ReminderController {
    private final ReminderService reminderService;

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReminderDto create(@RequestBody(required = false) ReminderCreateDto request) {
        return reminderService.create(request);
    }

    @GetMapping
    public List<ReminderDto> list(@RequestParam(required = false) String scope) {
        return reminderService.list(scope);
    }

    @PutMapping("/{reminderId}")
    public ReminderDto update(
            @PathVariable Long reminderId,
            @RequestBody(required = false) ReminderUpdateDto request) {
        return reminderService.update(reminderId, request);
    }

    @PatchMapping("/{reminderId}/complete")
    public ReminderDto complete(
            @PathVariable Long reminderId,
            @RequestBody(required = false) ReminderCompleteDto request) {
        return reminderService.complete(reminderId, request);
    }

    @DeleteMapping("/{reminderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long reminderId, @RequestParam Integer version) {
        reminderService.delete(reminderId, version);
    }
}
