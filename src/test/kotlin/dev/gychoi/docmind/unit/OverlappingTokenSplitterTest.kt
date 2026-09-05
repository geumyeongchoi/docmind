package dev.gychoi.docmind.unit

import dev.gychoi.docmind.infra.ingest.OverlappingTokenSplitter
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class OverlappingTokenSplitterTest {
    private val splitter = OverlappingTokenSplitter(chunkSizeTokens = 100, overlapTokens = 20)

    @Test
    fun `짧은 텍스트는 청크 1개`() {
        splitter.split("연차는 다음 해로 이월할 수 없습니다.") shouldHaveSize 1
    }

    @Test
    fun `긴 텍스트는 chunkSize 이하 조각으로 나뉘고 인접 청크가 겹치며 텍스트가 유실되지 않는다`() {
        // 문장마다 고유 번호를 넣어 유실 여부를 검증할 수 있게 한다
        val sentences = (1..80).map { "문장 $it 번은 재택근무 신청 규정에 대한 내용입니다." }
        val text = sentences.joinToString(" ")
        val chunks = splitter.split(text)

        chunks.size shouldBeGreaterThan 3
        chunks.forEach { splitter.countTokens(it) shouldBeLessThanOrEqual 100 }

        // 유실 없음: 모든 문장 번호가 어느 청크에든 존재
        val joined = chunks.joinToString("\n")
        (1..80).forEach { n -> joined shouldContain "문장 $n 번" }

        // 오버랩: 다음 청크의 앞부분이 이전 청크 끝에도 존재
        for (i in 0 until chunks.size - 1) {
            val head = chunks[i + 1].take(12)
            chunks[i].contains(head) shouldBe true
        }
    }

    @Test
    fun `빈 문자열은 빈 리스트`() {
        splitter.split("   \n ") shouldHaveSize 0
    }
}
