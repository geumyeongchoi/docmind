package dev.gychoi.docmind.support

import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.metadata.DefaultUsage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.document.Document
import org.springframework.ai.embedding.Embedding
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import reactor.core.publisher.Flux
import java.security.MessageDigest
import kotlin.math.sqrt

/**
 * 테스트용 가짜 모델. 외부 네트워크 0.
 *  - FakeEmbeddingModel: 단어 해시 기반 1536차원 결정적 벡터 → 같은 단어를 공유하는 텍스트가 가까워진다(검색 테스트 가능)
 *  - FakeChatModel: 컨텍스트 첫 줄을 그대로 되돌려주는 스트림(토큰 3개로 쪼개서 SSE 순서 검증)
 */
@TestConfiguration
class FakeAiConfig {
    @Bean @Primary
    fun fakeEmbeddingModel(): EmbeddingModel = FakeEmbeddingModel(1536)

    @Bean @Primary
    fun fakeChatModel(): ChatModel = FakeChatModel()
}

class FakeEmbeddingModel(
    private val dim: Int,
) : EmbeddingModel {
    override fun call(request: EmbeddingRequest): EmbeddingResponse =
        EmbeddingResponse(request.instructions.mapIndexed { i, t -> Embedding(embed(t), i) })

    override fun embed(document: Document): FloatArray = embed(document.text ?: "")

    override fun embed(text: String): FloatArray {
        val v = FloatArray(dim)
        text.lowercase().split(Regex("[^\\p{L}\\p{N}_]+")).filter { it.length > 1 }.forEach { w ->
            val h = MessageDigest.getInstance("MD5").digest(w.toByteArray())
            for (k in 0 until 4) {
                val idx = ((h[k * 2].toInt() and 0xff) shl 8 or (h[k * 2 + 1].toInt() and 0xff)) % dim
                v[idx] += 1f
            }
        }
        val norm = sqrt(v.sumOf { (it * it).toDouble() }).toFloat().takeIf { it > 0 } ?: 1f
        for (i in v.indices) v[i] /= norm
        return v
    }

    override fun dimensions(): Int = dim
}

class FakeChatModel : ChatModel {
    var lastPrompt: Prompt? = null
    var callCount = 0

    override fun call(prompt: Prompt): ChatResponse {
        lastPrompt = prompt
        callCount++
        return ChatResponse(
            listOf(Generation(AssistantMessage(reply(prompt)))),
            ChatResponseMetadata.builder().usage(DefaultUsage(10, 5)).build(),
        )
    }

    override fun stream(prompt: Prompt): Flux<ChatResponse> {
        lastPrompt = prompt
        callCount++
        val text = reply(prompt)
        val a = text.length / 3
        val b = text.length * 2 / 3
        val parts = listOf(text.substring(0, a), text.substring(a, b), text.substring(b))
        val usage = ChatResponseMetadata.builder().usage(DefaultUsage(10, 5)).build()
        return Flux
            .fromIterable(parts.map { ChatResponse(listOf(Generation(AssistantMessage(it)))) })
            .concatWithValues(ChatResponse(listOf(Generation(AssistantMessage(""))), usage))
    }

    override fun getDefaultOptions(): ChatOptions = ChatOptions.builder().model("fake-chat").build()

    private fun reply(prompt: Prompt): String {
        val sys = prompt.instructions.firstOrNull { it.messageType.name == "SYSTEM" }?.text ?: ""
        val ctx =
            sys
                .substringAfter("[문서 컨텍스트]", "")
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("[") }
        return "FAKE_ANSWER: " + (ctx.firstOrNull() ?: "no-context")
    }
}
