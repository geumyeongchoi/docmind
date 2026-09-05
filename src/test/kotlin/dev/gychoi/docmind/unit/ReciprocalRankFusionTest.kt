package dev.gychoi.docmind.unit

import dev.gychoi.docmind.infra.vector.HybridRetriever
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.ai.document.Document

class ReciprocalRankFusionTest {
    private fun doc(id: String) =
        Document
            .builder()
            .id(id)
            .text("t-$id")
            .metadata(mapOf<String, Any>())
            .build()

    @Test
    fun `두 리스트에 모두 등장한 문서가 위로 온다 - A C B D`() {
        val vector = listOf(doc("A"), doc("B"), doc("C"))
        val fts = listOf(doc("C"), doc("A"), doc("D"))

        val fused = HybridRetriever.reciprocalRankFusion(listOf(vector, fts), k = 60)

        fused.map { it.id } shouldBe listOf("A", "C", "B", "D")
        // A: 1/61 + 1/62,  C: 1/63 + 1/61 → A > C
        (fused[0].score!! > fused[1].score!!) shouldBe true
    }

    @Test
    fun `한 리스트가 비어도 동작`() {
        val fused = HybridRetriever.reciprocalRankFusion(listOf(listOf(doc("X")), emptyList()))
        fused.map { it.id } shouldBe listOf("X")
    }
}
