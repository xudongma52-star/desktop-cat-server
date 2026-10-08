package com.desktopcat.server.knowledge;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.desktopcat.server.knowledge.dao.KnowledgeChatDO;
import com.desktopcat.server.knowledge.dao.KnowledgeChatDao;
import com.desktopcat.server.knowledge.dao.KnowledgeMessageDO;
import com.desktopcat.server.knowledge.dao.KnowledgeMessageDao;
import com.desktopcat.server.knowledge.service.KnowledgeChatPersistenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class KnowledgeChatPersistenceServiceTest {

    @Test
    void doesNotInsertMessagesWhenChatVersionHasChanged() {
        KnowledgeChatDao chatDao = mock(KnowledgeChatDao.class);
        KnowledgeMessageDao messageDao = mock(KnowledgeMessageDao.class);
        KnowledgeChatPersistenceService service = new KnowledgeChatPersistenceService(
                chatDao, messageDao, new ObjectMapper());
        KnowledgeChatDO chat = new KnowledgeChatDO();
        chat.setChatId(12L);
        chat.setUserId(7L);
        chat.setTitle("旧对话");
        chat.setVersion(2);
        chat.setCreatedAt(Instant.EPOCH);
        chat.setUpdatedAt(Instant.EPOCH);
        when(chatDao.updateAfterMessage(eq(7L), eq(12L), eq(2), any(), eq(chat))).thenReturn(0);

        assertThatThrownBy(() -> service.appendTurn(
                7L, chat, "继续提问", "新的回答", List.of()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Knowledge chat has been updated");

        verify(messageDao, never()).insertMessage(any(KnowledgeMessageDO.class));
    }
}
