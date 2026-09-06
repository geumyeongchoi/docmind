package dev.gychoi.docmind.domain

import java.util.UUID

/** 검색 포트 — 벡터 단독 / 하이브리드 / 재순위 구현체를 교체할 수 있다. */
fun interface Retriever {
    fun retrieve(query: String): List<Chunk>
}

interface DocumentRepository {
    fun save(document: Document): Document

    fun update(document: Document): Document

    fun findById(id: UUID): Document?

    fun findBySha256(
        sha256: String,
        profile: String,
    ): Document?

    fun findAll(limit: Int = 200): List<Document>

    fun delete(id: UUID)
}

/** 문서 → 청크 텍스트 + 벡터 저장 포트. */
interface ChunkStore {
    fun addChunks(
        document: Document,
        chunks: List<ChunkDraft>,
    ): Int

    fun deleteByDocument(documentId: UUID)

    /** 뷰어용: 문서의 청크를 chunkIndex 순으로. 벡터 없이 텍스트·페이지·인덱스만. */
    fun listByDocument(documentId: UUID): List<Chunk>

    fun count(): Long
}

/** 원본 파일 보관 포트 — 출처에서 원문으로 이동하기 위해 필요. 기본 구현은 PostgreSQL BYTEA, 대용량이면 오브젝트 스토리지로 교체. */
interface DocumentFileStore {
    fun save(
        documentId: UUID,
        contentType: String?,
        bytes: ByteArray,
    )

    fun load(documentId: UUID): StoredFile?
}

class StoredFile(
    val contentType: String?,
    val bytes: ByteArray,
)

data class ChunkDraft(
    val content: String,
    val page: Int?,
    val chunkIndex: Int,
)

/** 파싱 + 청킹 포트. 구현체(Tika + 오버랩 토큰 분할)는 infra.ingest. */
interface DocumentParser {
    fun parse(
        bytes: ByteArray,
        filename: String,
        contentType: String?,
    ): List<ChunkDraft>
}

/** 답변 생성 포트 — 스트리밍 이벤트를 콜백으로 밀어낸다. */
interface AnswerGenerator {
    fun stream(
        sessionId: String,
        question: String,
        context: List<Chunk>,
        onEvent: (AnswerEvent) -> Unit,
    )

    fun modelName(): String?
}
