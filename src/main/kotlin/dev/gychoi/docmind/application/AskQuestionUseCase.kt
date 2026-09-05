package dev.gychoi.docmind.application

import dev.gychoi.docmind.config.DocmindProperties
import dev.gychoi.docmind.domain.AnswerEvent
import dev.gychoi.docmind.domain.AnswerGenerator
import dev.gychoi.docmind.domain.Chunk
import dev.gychoi.docmind.domain.Citation
import dev.gychoi.docmind.domain.NO_ANSWER_MESSAGE
import dev.gychoi.docmind.domain.Retriever
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AskQuestionUseCase(
    private val retriever: Retriever,
    private val generator: AnswerGenerator,
    private val props: DocmindProperties,
    private val meters: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 검색 → (근거 없으면 LLM 미호출) → 스트리밍 생성.
     * 이벤트 순서: Citations → Token* → Done.  ADR-6: 근거가 없으면 LLM을 호출하지 않는다.
     */
    fun ask(
        sessionId: String,
        question: String,
        onEvent: (AnswerEvent) -> Unit,
    ) {
        val started = System.currentTimeMillis()
        val chunks = retriever.retrieve(question)
        meters.summary("docmind.retrieval.hits").record(chunks.size.toDouble())

        val citations = chunks.map(Citation::from)
        onEvent(AnswerEvent.Citations(citations))

        if (chunks.isEmpty()) {
            meters.counter("docmind.no_answer").increment()
            onEvent(AnswerEvent.Token(NO_ANSWER_MESSAGE))
            onEvent(
                AnswerEvent.Done(
                    System.currentTimeMillis() - started,
                    props.profileLabel,
                    generator.modelName(),
                    null,
                    null,
                    answered = false,
                ),
            )
            return
        }

        generator.stream(sessionId, question, chunks) { ev ->
            when (ev) {
                is AnswerEvent.Done -> onEvent(ev.copy(elapsedMs = System.currentTimeMillis() - started, profile = props.profileLabel))
                else -> onEvent(ev)
            }
        }
    }

    /** MCP search_docs 등 검색만 필요한 경우. */
    fun search(query: String): List<Chunk> = retriever.retrieve(query)
}
