package com.desktopcat.server.capture.client;

import com.desktopcat.server.capture.dto.CaptureAiArticleRequestDto;
import com.desktopcat.server.capture.dto.CaptureAiArticleResponseDto;
import com.desktopcat.server.capture.dto.CaptureAiClassificationRequestDto;
import com.desktopcat.server.capture.dto.CaptureAiClassificationResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/** 调用 Python 整理服务；日志只记录数量和错误类型，不记录私人正文或图片。 */
@Component
@Profile("postgres")
public class CaptureAiClient {
    private static final Logger log = LoggerFactory.getLogger(CaptureAiClient.class);
    private final RestClient restClient;

    public CaptureAiClient(
            RestClient.Builder builder,
            @Value("${desktop-cat.rag.service-url:http://127.0.0.1:8090}") String serviceUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2_000);
        requestFactory.setReadTimeout(190_000);
        this.restClient = builder.baseUrl(serviceUrl).requestFactory(requestFactory).build();
    }

    public CaptureAiClassificationResponseDto classify(CaptureAiClassificationRequestDto request) {
        return post("/internal/captures/classify", request,
                CaptureAiClassificationResponseDto.class, "capture_classification");
    }

    public CaptureAiArticleResponseDto draftArticle(CaptureAiArticleRequestDto request) {
        return post("/internal/captures/article", request,
                CaptureAiArticleResponseDto.class, "capture_article");
    }

    private <T> T post(String uri, Object request, Class<T> responseType, String event) {
        try {
            T response = restClient.post().uri(uri).body(request).retrieve().body(responseType);
            if (response == null) {
                log.warn("event={}_invalid_response", event);
                throw unavailable();
            }
            return response;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.warn("event={}_request_failed errorType={}", event,
                    exception.getClass().getSimpleName());
            throw unavailable();
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "AI organization service is unavailable.");
    }
}
