package com.depromeet.team1.infra.storage.postgresql.chat

import com.depromeet.team1.core.chat.Conversation
import com.depromeet.team1.core.chat.ConversationRepository
import com.depromeet.team1.core.chat.Message
import com.depromeet.team1.core.chat.MessageRepository
import com.depromeet.team1.core.chat.MessageRole
import com.depromeet.team1.core.chat.MessageSavedItem
import com.depromeet.team1.core.chat.MessageStatus
import com.depromeet.team1.infra.storage.postgresql.StorageTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JpaChatRepositoryTest : StorageTest() {
    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Autowired
    private lateinit var messageRepository: MessageRepository

    @Test
    fun `저장한 대화를 ID 로 조회한다`() {
        val saved = conversationRepository.save(Conversation.create(1, NOW))

        assertEquals(saved, conversationRepository.findById(assertNotNull(saved.id)))
    }

    @Test
    fun `대화의 메시지를 저장한 순서대로 조회한다`() {
        val conversationId = conversationRepository.save(Conversation.create(1, NOW)).id!!
        val question = messageRepository.save(message(conversationId, MessageRole.USER, "카페 어디 저장했지?"))
        val answer = messageRepository.save(message(conversationId, MessageRole.ASSISTANT, "성수 카페가 있어요"))

        val messages = messageRepository.findByConversationId(conversationId)

        assertEquals(listOf(question, answer), messages)
    }

    @Test
    fun `답변이 찾아 준 저장물을 관련도순으로 조회한다`() {
        val conversationId = conversationRepository.save(Conversation.create(1, NOW)).id!!
        val answerId = messageRepository.save(message(conversationId, MessageRole.ASSISTANT, "답변")).id!!
        messageRepository.saveSavedItems(
            listOf(MessageSavedItem(answerId, 20, 1), MessageSavedItem(answerId, 10, 0)),
        )

        val savedItems = messageRepository.findSavedItems(answerId)

        assertEquals(listOf(10L, 20L), savedItems.map { it.savedItemId })
    }

    private fun message(conversationId: Long, role: MessageRole, content: String) =
        Message(null, conversationId, role, content, MessageStatus.DONE, NOW)
}
