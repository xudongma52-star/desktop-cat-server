package com.desktopcat.server.events;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 管理服务器向客户端持续推送消息的 SSE 连接。
 */
@Component
public class AssistantEventStream {
    private static final Logger log = LoggerFactory.getLogger(AssistantEventStream.class);

    // 由心跳检测失效连接，Spring 这一层不主动按固定时间终止连接。
    private static final long NO_SERVER_TIMEOUT = 0L;

    // 连接、心跳和业务发布可能由不同线程执行，因此使用线程安全的 Map。
    private final Map<String, SseEmitter> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe() {
        String subscriberId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(NO_SERVER_TIMEOUT);

        // 先保存连接，再注册回调，确保任何结束路径都可以执行统一清理。
        subscribers.put(subscriberId, emitter);
        emitter.onCompletion(() -> removeSubscriber(
                subscriberId, emitter, "completed", null));
        emitter.onTimeout(() -> removeSubscriber(
                subscriberId, emitter, "timeout", null));
        emitter.onError(error -> removeSubscriber(
                subscriberId, emitter, "client_error", error));

        try {
            emitter.send(SseEmitter.event()
                    .name("connection.ready")
                    .data(new EventStreamReady(Instant.now())));
            log.debug("event=sse_subscriber_connected subscriberId={} subscriberCount={}",
                    subscriberId, subscribers.size());
        } catch (IOException | IllegalStateException exception) {
            removeSubscriber(subscriberId, emitter, "initial_send_failed", exception);
        }
        return emitter;
    }

    //向所有 SSE 客户端广播一条“资料已更新”事件
    public void publishCatProfileUpdated(long profileId, int version) {
        publish(SseEmitter.event()
                .id(profileId + "-" + version)
                .name("cat-profile.updated")
                .data(new CatProfileUpdatedEvent(profileId, version)));
    }

    /** 事务提交成功后再通知客户端，避免客户端读取到尚未提交的提醒状态。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishReminderChanged(ReminderChangedEvent event) {
        publish(SseEmitter.event()
                .id("reminder-" + event.reminderId() + "-" + event.version() + "-" + event.action())
                .name("reminder.changed")
                .data(event));
    }

    //定时发送心跳
    //@Scheduled 表示这个方法由 Spring 定时调用。项目启动类上存在：
    @Scheduled(fixedDelayString = "${app.events.heartbeat-ms:25000}")
    void sendHeartbeat() {
        // 定期写入注释帧，既保持连接活跃，也能及时发现已经离线的客户端。
        publish(SseEmitter.event().comment("keepalive"));
    }

    private void publish(SseEmitter.SseEventBuilder event) {
        subscribers.forEach((subscriberId, emitter) -> {
            try {
                emitter.send(event);
            } catch (IOException | IllegalStateException exception) {
                // send 抛出 I/O 异常后由 Servlet 容器完成异步请求，这里只清理本地连接。
                removeSubscriber(subscriberId, emitter, "send_failed", exception);
            }
        });
    }

    private void removeSubscriber(
            //sse连接编号
            String subscriberId,
            //具体的连接对象
            SseEmitter emitter,
            //清理原因
            String reason,
            Throwable error) {
        // 完成、超时和异常回调可能连续触发；条件删除保证只清理和记录一次。
        if (!subscribers.remove(subscriberId, emitter)) {
            return;
        }

        //获取删除后的连接数量
        int subscriberCount = subscribers.size();
        if ("timeout".equals(reason)) {
            log.warn("event=sse_subscriber_removed reason={} subscriberId={} subscriberCount={}",
                    reason, subscriberId, subscriberCount);
            return;
        }

        if (error != null) {
            // 客户端刷新或关闭页面也会进入此分支，因此使用 DEBUG，避免产生大量告警。
            log.debug("event=sse_subscriber_removed reason={} subscriberId={} "
                            + "subscriberCount={} errorType={}",
                    reason, subscriberId, subscriberCount, error.getClass().getSimpleName());
            return;
        }

        log.debug("event=sse_subscriber_removed reason={} subscriberId={} subscriberCount={}",
                reason, subscriberId, subscriberCount);
    }
}
