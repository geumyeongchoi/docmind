package dev.gychoi.docmind.unit

import dev.gychoi.docmind.application.AskQuestionUseCase
import dev.gychoi.docmind.config.DocmindProperties
import dev.gychoi.docmind.domain.AnswerEvent
import dev.gychoi.docmind.domain.AnswerGenerator
import dev.gychoi.docmind.domain.Chunk
import dev.gychoi.docmind.domain.NO_ANSWER_MESSAGE
import dev.gychoi.docmind.domain.Retriever
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class AskQuestionUseCaseTest {
    private val generator = mockk<AnswerGenerator>(relaxed = true)
    private val props = DocmindProperties(profileLabel = "test")

    @Test
    fun `검색 결과가 없으면 LLM을 호출하지 않고 고정 응답을 낸다`() {
        val retriever = Retriever { emptyList() }
        val useCase = AskQuestionUseCase(retriever, generator, props, SimpleMeterRegistry())
        val events = ArrayList<AnswerEvent>()

        useCase.ask("s1", "회사 창립일은?") { events += it }

        events[0].shouldBeInstanceOf<AnswerEvent.Citations>().items shouldBe emptyList()
        events[1].shouldBeInstanceOf<AnswerEvent.Token>().text shouldBe NO_ANSWER_MESSAGE
        events[2].shouldBeInstanceOf<AnswerEvent.Done>().answered shouldBe false
        verify(exactly = 0) { generator.stream(any(), any(), any(), any()) }
    }

    @Test
    fun `검색 결과가 있으면 citations를 먼저 보내고 생성기를 호출한다`() {
        val chunk = Chunk("c1", null, "hr-policy.md", 3, 0, "연차는 이월 불가", 0.9)
        val retriever = Retriever { listOf(chunk) }
        every { generator.stream(any(), any(), any(), any()) } answers {
            val cb = arg<(AnswerEvent) -> Unit>(3)
            cb(AnswerEvent.Token("연차는 "))
            cb(AnswerEvent.Token("이월 불가"))
            cb(AnswerEvent.Done(0, "", "m", 1, 1, true))
        }
        val useCase = AskQuestionUseCase(retriever, generator, props, SimpleMeterRegistry())
        val events = ArrayList<AnswerEvent>()

        useCase.ask("s1", "연차 이월?") { events += it }

        events[0]
            .shouldBeInstanceOf<AnswerEvent.Citations>()
            .items
            .single()
            .filename shouldBe "hr-policy.md"
        events.filterIsInstance<AnswerEvent.Token>().joinToString("") { it.text } shouldBe "연차는 이월 불가"
        val done = events.last().shouldBeInstanceOf<AnswerEvent.Done>()
        done.profile shouldBe "test"
        done.answered shouldBe true
    }
}
