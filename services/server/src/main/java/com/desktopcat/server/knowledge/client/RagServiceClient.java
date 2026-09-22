package com.desktopcat.server.knowledge.client;

import com.desktopcat.server.knowledge.dto.RagRetrieveRequestDto;
import com.desktopcat.server.knowledge.dto.RagRetrieveResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/** 调用本机 Python 知识检索服务，不向日志写入问题或个人正文。 */
@Component
@Profile("postgres")
public class RagServiceClient {
    private static final Logger log = LoggerFactory.getLogger(RagServiceClient.class);

    private final RestClient restClient;

    public RagServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${desktop-cat.rag.service-url:http://127.0.0.1:8090}") String serviceUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2_000);
        // Python 最多等待豆包 110 秒；Java 的整条调用上限为 2 分钟。
        requestFactory.setReadTimeout(120_000);
        this.restClient = restClientBuilder
                .baseUrl(serviceUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public RagRetrieveResponseDto retrieve(RagRetrieveRequestDto request) {
        try {
            RagRetrieveResponseDto response = restClient.post()
                    .uri("/internal/rag/retrieve")
                    .body(request)
                    .retrieve()
                    .body(RagRetrieveResponseDto.class);
            if (response == null || response.matches() == null) {
                log.warn("event=rag_service_invalid_response");
                throw unavailable();
            }
            return response;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.warn("event=rag_service_request_failed errorType={}",
                    exception.getClass().getSimpleName());
            throw unavailable();
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Knowledge retrieval service is unavailable.");
    }
}
