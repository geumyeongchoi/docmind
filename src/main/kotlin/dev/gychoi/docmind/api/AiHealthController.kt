package dev.gychoi.docmind.api

import dev.gychoi.docmind.config.DocmindProperties
import dev.gychoi.docmind.domain.AnswerGenerator
import dev.gychoi.docmind.domain.ChunkStore
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class AiHealthController(
    private val props: DocmindProperties,
    private val generator: AnswerGenerator,
    private val embeddingModel: EmbeddingModel,
    private val chunkStore: ChunkStore,
    @Value("\${spring.ai.vectorstore.pgvector.table-name}") private val tableName: String,
    @Value("\${spring.ai.vectorstore.pgvector.dimensions}") private val dimensions: Int,
) {
    /** 프로파일·모델·차원·벡터 row 수 — 임베딩 차원 불일치를 배포 직후 잡아내는 용도. */
    @GetMapping("/api/health/ai")
    fun health(): Map<String, Any?> {
        val actualDim = runCatching { embeddingModel.dimensions() }.getOrNull()
        return mapOf(
            "profile" to props.profileLabel,
            "index" to props.effectiveIndexLabel,
            "chatModel" to generator.modelName(),
            "chatProvider" to generator.javaClass.simpleName,
            "embeddingProvider" to embeddingModel.javaClass.simpleName,
            "embeddingDimensions" to actualDim,
            "configuredDimensions" to dimensions,
            "dimensionsMatch" to (actualDim == null || actualDim == dimensions),
            "vectorTable" to tableName,
            "vectorRows" to chunkStore.count(),
            "hybrid" to props.retrieval.hybrid,
            "chunkSizeTokens" to props.ingest.chunkSizeTokens,
        )
    }
}
