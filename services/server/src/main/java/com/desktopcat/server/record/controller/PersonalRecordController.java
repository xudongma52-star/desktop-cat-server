package com.desktopcat.server.record.controller;

import com.desktopcat.server.record.service.PersonalRecordService;
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

@RestController
@Profile("postgres")
@RequestMapping("/api/records")
public class PersonalRecordController {
    private final PersonalRecordService personalRecordService;

    public PersonalRecordController(PersonalRecordService personalRecordService) {
        this.personalRecordService = personalRecordService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PersonalRecordResponse createRecord(
            @RequestBody(required = false) CreatePersonalRecordRequest request) {
        return PersonalRecordResponse.from(personalRecordService.createRecord(
                request == null ? null : request.toDataObject()));
    }

    @GetMapping
    public PersonalRecordPageResponse listRecords(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "12") Integer pageSize,
            @RequestParam(required = false) String recordType) {
        return PersonalRecordPageResponse.from(
                personalRecordService.listRecords(page, pageSize, recordType));
    }

    @GetMapping("/{recordId}")
    public PersonalRecordResponse getRecord(@PathVariable Long recordId) {
        return PersonalRecordResponse.from(personalRecordService.getRecord(recordId));
    }

    @PutMapping("/{recordId}")
    public PersonalRecordResponse updateRecord(
            @PathVariable Long recordId,
            @RequestBody(required = false) UpdatePersonalRecordRequest request) {
        return PersonalRecordResponse.from(personalRecordService.updateRecord(
                recordId, request == null ? null : request.toDataObject()));
    }

    @DeleteMapping("/{recordId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRecord(
            @PathVariable Long recordId,
            @RequestParam(required = false) Integer version) {
        personalRecordService.deleteRecord(recordId, version);
    }

    @GetMapping("/recalls")
    public List<PersonalRecordRecallResponse> listRecalls(
            @RequestParam(defaultValue = "10") Integer limit) {
        return personalRecordService.listRecalls(limit).stream()
                .map(PersonalRecordRecallResponse::from)
                .toList();
    }
}
