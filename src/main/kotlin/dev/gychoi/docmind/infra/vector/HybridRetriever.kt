package dev.gychoi.docmind.infra.vector

import dev.gychoi.docmind.config.DocmindProperties
import dev.gychoi.docmind.domain.Chunk
import dev.gychoi.docmind.domain.Retriever
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * 벡터 검색 + PostgreSQL 전문검색을 병렬 실행하고 RRF로 병합한다(설계 문서 §6-3, ADR-3).
 * hybrid=false 이면 벡터 단독(평가 비교용).
 */
@Component
class HybridRetriever(
    private val vectorStore: VectorStore,
    private val jdbc: JdbcTemplate,
    private val props: DocmindProperties,
    private val meters: MeterRegistry,
    @Value("\${spring.ai.vectorstore.pgvector.table-name}") private val tableName: String,
) : Retriever {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun retrieve(query: String): List<Chunk> {
        val r = props.retrieval
        val fetch = r.topK * 2
        val sample =
            io.micrometer.core.instrument.Timer
                .start(meters)
        try {
            val vectorFuture = CompletableFuture.supplyAsync { vectorSearch(query, fetch, r.similarityThreshold) }
            if (!r.hybrid) return vectorFuture.get().take(r.topK).map(::toChunk)

            val ftsFuture = CompletableFuture.supplyAsync { fullTextSearch(query, fetch) }
            val vector = vectorFuture.get()
            val fts = ftsFuture.get()
            log.debug("retrieve q='{}' vector={} fts={}", query.take(60), vector.size, fts.size)
            return reciprocalRankFusion(listOf(vector, fts), r.rrfK).take(r.topK).map(::toChunk)
        } finally {
            sample.stop(meters.timer("docmind.retrieval.time"))
        }
    }

    private fun vectorSearch(
        query: String,
        topK: Int,
        threshold: Double,
    ): List<Document> =
        vectorStore.similaritySearch(
            SearchRequest
                .builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(threshold)
                .build(),
        ) ?: emptyList()

    /** 한국어는 'simple' 사전 + 공백 토큰. websearch_to_tsquery는 따옴표/OR/- 문법을 안전하게 처리한다. */
    private fun fullTextSearch(
        query: String,
        limit: Int,
    ): List<Document> =
        jdbc.query(
            """
            SELECT id::text AS id, content, metadata::text AS metadata,
                   ts_rank_cd(content_tsv, websearch_to_tsquery('simple', ?)) AS rank
            FROM $tableName
            WHERE content_tsv @@ websearch_to_tsquery('simple', ?)
            ORDER BY rank DESC
            LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                val meta = parseMetadata(rs.getString("metadata"))
                Document
                    .builder()
                    .id(rs.getString("id"))
                    .text(rs.getString("content"))
                    .metadata(meta)
                    .score(rs.getDouble("rank"))
                    .build()
            },
            query,
            query,
            limit,
        )

    private fun parseMetadata(json: String?): Map<String, Any> =
        if (json.isNullOrBlank()) emptyMap() else OBJECT_MAPPER.readValue(json, MAP_TYPE)

    private fun toChunk(d: Document): Chunk {
        val m = d.metadata
        return Chunk(
            id = d.id,
            documentId = (m["documentId"] as? String)?.let { runCatching { UUID.fromString(it) }.getOrNull() },
            filename = m["filename"]?.toString() ?: "unknown",
            page = (m["page"] as? Number)?.toInt(),
            chunkIndex = (m["chunkIndex"] as? Number)?.toInt(),
            content = d.text ?: "",
            score = d.score,
        )
    }

    companion object {
        private val OBJECT_MAPPER =
            com.fasterxml.jackson.databind
                .ObjectMapper()
        private val MAP_TYPE = OBJECT_MAPPER.typeFactory.constructMapType(Map::class.java, String::class.java, Any::class.java)

        /** RRF: score(d) = Σ 1/(k + rank_i(d)), rank는 1부터. (Cormack et al., 2009) */
        fun reciprocalRankFusion(
            lists: List<List<Document>>,
            k: Int = 60,
        ): List<Document> {
            val score = LinkedHashMap<String, Double>()
            val byId = HashMap<String, Document>()
            for (list in lists) {
                list.forEachIndexed { idx, doc ->
                    score.merge(doc.id, 1.0 / (k + idx + 1), Double::plus)
                    byId.putIfAbsent(doc.id, doc)
                }
            }
            return score.entries.sortedByDescending { it.value }.map { (id, s) ->
                val d = byId.getValue(id)
                Document
                    .builder()
                    .id(d.id)
                    .text(d.text)
                    .metadata(d.metadata)
                    .score(s)
                    .build()
            }
        }
    }
}
