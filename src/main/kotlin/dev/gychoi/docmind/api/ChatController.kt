package dev.gychoi.docmind.api

import com.fasterxml.jackson.databind.ObjectMapper
import dev.gychoi.docmind.application.AskQuestionUseCase
import dev.gychoi.docmind.domain.AnswerEvent
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class ChatRequest(
    @field:NotBlank @field:Size(max = 128) val sessionId: String,
    @field:NotBlank @field:Size(max = 4000) val question: String,
)

/**
 * SSE 이벤트 순서: citations → token* → done.  (설계 문서 §8)
 * SseEmitter + 전용 스레드: 검색·LLM 스트리밍이 서블릿 스레드를 점유하지 않도록 한다.
 */
@RestController
@RequestMapping("/api/chat")
@Validated
class ChatController(
    private val ask: AskQuestionUseCase,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val executor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

    @PostMapping(produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun chat(
        @RequestBody @Validated req: ChatRequest,
    ): SseEmitter {
        val emitter = SseEmitter(180_000L)
        executor.submit {
            try {
                ask.ask(req.sessionId, req.question) { ev -> send(emitter, ev) }
                emitter.complete()
            } catch (e: Exception) {
                log.warn("chat failed session={}: {}", req.sessionId, e.toString())
                runCatching { emitter.send(SseEmitter.event().name("error").data(mapOf("message" to (e.message ?: "error")))) }
                emitter.completeWithError(e)
            }
        }
        return emitter
    }

    private fun send(
        emitter: SseEmitter,
        ev: AnswerEvent,
    ) {
        val (name, payload) =
            when (ev) {
                is AnswerEvent.Citations -> "citations" to ev.items
                is AnswerEvent.Token -> "token" to mapOf("text" to ev.text)
                is AnswerEvent.Done -> "done" to ev
            }
        emitter.send(SseEmitter.event().name(name).data(objectMapper.writeValueAsString(payload), MediaType.APPLICATION_JSON))
    }
}
