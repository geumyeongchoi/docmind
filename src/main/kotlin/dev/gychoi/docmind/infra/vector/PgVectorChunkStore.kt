package dev.gychoi.docmind.infra.vector

import dev.gychoi.docmind.config.DocmindProperties
import dev.gychoi.docmind.domain.Chunk
import dev.gychoi.docmind.domain.ChunkDraft
import dev.gychoi.docmind.domain.ChunkStore
import dev.gychoi.docmind.domain.Document
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Spring AI VectorStore(pgvector) 어댑터. 임베딩은 VectorStore.add 내부에서 배치로 수행된다.
 * 청크 metadata 규약: documentId, filename, page, chunkIndex, profile  (설계 문서 §9)
 */
@Component
class PgVectorChunkStore(
    private val vectorStore: VectorStore,
    private val jdbc: JdbcTemplate,
    private val props: DocmindProperties,
    @Value("\${spring.ai.vectorstore.pgvector.table-name}") private val tableName: String,
) : ChunkStore {
    override fun addChunks(
        document: Document,
        chunks: List<ChunkDraft>,
    ): Int {
        val docs =
            chunks.map { c ->
                val meta = HashMap<String, Any>()
                meta["documentId"] = document.id.toString()
                meta["filename"] = document.filename
                meta["chunkIndex"] = c.chunkIndex
                meta["profile"] = document.profile
                c.page?.let { meta["page"] = it }
                org.springframework.ai.document
                    .Document(UUID.randomUUID().toString(), c.content, meta)
            }
        docs.chunked(props.ingest.embeddingBatchSize).forEach { vectorStore.add(it) }
        return docs.size
    }

    override fun deleteByDocument(documentId: UUID) {
        // 라이브러리 필터 표현식 대신 SQL — metadata->>'documentId' 표현식 인덱스를 탄다 (V2 마이그레이션)
        jdbc.update("DELETE FROM $tableName WHERE metadata->>'documentId' = ?", documentId.toString())
    }

    override fun listByDocument(documentId: UUID): List<Chunk> =
        jdbc.query(
            """
            SELECT id::text AS id, content, metadata->>'filename' AS filename,
                   (metadata->>'page')::int AS page, (metadata->>'chunkIndex')::int AS chunk_index
            FROM $tableName
            WHERE metadata->>'documentId' = ?
            ORDER BY (metadata->>'chunkIndex')::int
            """.trimIndent(),
            { rs, _ ->
                Chunk(
                    id = rs.getString("id"),
                    documentId = documentId,
                    filename = rs.getString("filename") ?: "unknown",
                    page = rs.getObject("page")?.let { (it as Number).toInt() },
                    chunkIndex = rs.getObject("chunk_index")?.let { (it as Number).toInt() },
                    content = rs.getString("content"),
                    score = null,
                )
            },
            documentId.toString(),
        )

    override fun count(): Long = jdbc.queryForObject("SELECT count(*) FROM $tableName", Long::class.java) ?: 0
}
