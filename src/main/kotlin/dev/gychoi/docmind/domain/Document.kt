package dev.gychoi.docmind.domain

import java.time.Instant
import java.util.UUID

enum class DocumentStatus { QUEUED, PROCESSING, DONE, FAILED, DUPLICATE }

/** 업로드된 문서 1건의 메타와 인제스트 상태. 프레임워크 의존 없음. */
data class Document(
    val id: UUID,
    val filename: String,
    val sha256: String,
    val contentType: String?,
    val sizeBytes: Long,
    val status: DocumentStatus,
    val chunkCount: Int = 0,
    val elapsedMs: Long? = null,
    val error: String? = null,
    val profile: String,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    fun processing() = copy(status = DocumentStatus.PROCESSING, updatedAt = Instant.now())

    fun done(
        chunkCount: Int,
        elapsedMs: Long,
    ) = copy(status = DocumentStatus.DONE, chunkCount = chunkCount, elapsedMs = elapsedMs, error = null, updatedAt = Instant.now())

    fun failed(
        message: String,
        elapsedMs: Long,
    ) = copy(status = DocumentStatus.FAILED, error = message.take(2000), elapsedMs = elapsedMs, updatedAt = Instant.now())

    companion object {
        fun queued(
            filename: String,
            sha256: String,
            contentType: String?,
            sizeBytes: Long,
            profile: String,
        ) = Document(UUID.randomUUID(), filename, sha256, contentType, sizeBytes, DocumentStatus.QUEUED, profile = profile)

        fun duplicateOf(
            original: Document,
            filename: String,
        ) = original.copy(
            id = UUID.randomUUID(),
            filename = filename,
            status = DocumentStatus.DUPLICATE,
            chunkCount = 0,
            elapsedMs = 0,
            error = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
    }
}

/** 검색된 청크 = 답변의 근거. */
data class Chunk(
    val id: String,
    val documentId: UUID?,
    val filename: String,
    val page: Int?,
    val chunkIndex: Int?,
    val content: String,
    val score: Double?,
)

/** 출처. documentId + chunkIndex(+page)로 뷰어가 원문의 해당 위치로 이동한다. */
data class Citation(
    val documentId: UUID?,
    val filename: String,
    val page: Int?,
    val chunkIndex: Int?,
    val score: Double?,
    val snippet: String,
) {
    companion object {
        fun from(chunk: Chunk) =
            Citation(chunk.documentId, chunk.filename, chunk.page, chunk.chunkIndex, chunk.score, chunk.content.take(160))
    }
}

/** 스트리밍 답변 이벤트. 컨트롤러가 SSE로 그대로 매핑한다. */
sealed interface AnswerEvent {
    data class Citations(
        val items: List<Citation>,
    ) : AnswerEvent

    data class Token(
        val text: String,
    ) : AnswerEvent

    data class Done(
        val elapsedMs: Long,
        val profile: String,
        val model: String?,
        val promptTokens: Long?,
        val completionTokens: Long?,
        val answered: Boolean,
    ) : AnswerEvent
}

const val NO_ANSWER_MESSAGE = "제공된 문서에서 찾지 못했습니다."
