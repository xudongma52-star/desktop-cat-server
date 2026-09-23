package com.desktopcat.server.emotion.service.impl;

import com.desktopcat.server.emotion.dao.EmotionDao;
import com.desktopcat.server.emotion.dao.EmotionDO;
import com.desktopcat.server.emotion.dto.EmotionCreateDto;
import com.desktopcat.server.emotion.dto.EmotionDto;
import com.desktopcat.server.emotion.service.EmotionService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("postgres")
public class EmotionServiceImpl implements EmotionService {
    private static final Logger log = LoggerFactory.getLogger(EmotionServiceImpl.class);
    //固定上海时区
    private static final ZoneId USER_ZONE = ZoneId.of("Asia/Shanghai");

    private final EmotionDao emotionDao;

    public EmotionServiceImpl(EmotionDao emotionDao) {
        this.emotionDao = emotionDao;
    }

    @Override
    @Transactional
    public EmotionDto create(long userId, EmotionCreateDto request) {
        if (request == null) {
            throw badRequest("Request body is required.");
        }
        if (request.content() == null || request.content().isBlank()) {
            throw badRequest("Emotion content is required.");
        }

        String content = request.content().strip();
        //获取当前 UTC 时间，Instant是java时间类型
        Instant now = Instant.now();
        EmotionDO emotion = new EmotionDO(null, userId, content, LocalDate.now(USER_ZONE), now);
        int insertedRows = emotionDao.insert(emotion);
        if (insertedRows != 1 || emotion.getEmotionId() == null) {
            log.error("event=emotion_create_failed userId={} contentLength={}",
                    userId, codePointLength(content));
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Emotion could not be created.");
        }

        log.info("event=emotion_created userId={} emotionId={} recordDate={} contentLength={}",
                userId, emotion.getEmotionId(), emotion.getRecordDate(), codePointLength(content));
        return toDto(emotion);
    }

    @Override
    public List<EmotionDto> listByDate(long userId, LocalDate recordDate) {
        LocalDate targetDate = recordDate == null ? LocalDate.now(USER_ZONE) : recordDate;
        return emotionDao.selectByDate(userId, targetDate).stream().map(this::toDto).toList();
    }

    private EmotionDto toDto(EmotionDO emotion) {
        return new EmotionDto(
                emotion.getEmotionId(), emotion.getContent(),
                emotion.getRecordDate(), emotion.getCreatedAt());
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }
}
