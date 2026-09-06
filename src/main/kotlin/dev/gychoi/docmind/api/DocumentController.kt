package dev.gychoi.docmind.api

import dev.gychoi.docmind.application.IngestDocumentUseCase
import dev.gychoi.docmind.application.UploadFile
import dev.gychoi.docmind.domain.Document
import dev.gychoi.docmind.domain.DocumentStatus
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.util.UUID

data class DocumentResponse(
    val id: UUID,
    val filename: String,
    val status: DocumentStatus,
    val chunkCount: Int,
    val elapsedMs: Long?,
    val error: String?,
    val sizeBytes: Long,
    val profile: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(d: Document) =
            DocumentResponse(
                d.id,
                d.filename,
                d.status,
                d.chunkCount,
                d.elapsedMs,
                d.error,
                d.sizeBytes,
                d.profile,
                d.createdAt,
                d.updatedAt,
            )
    }
}

data class ChunkResponse(
    val chunkIndex: Int?,
    val page: Int?,
    val content: String,
)

@RestController
@RequestMapping("/api/documents")
class DocumentController(
    private val ingest: IngestDocumentUseCase,
) {
    @PostMapping(consumes = ["multipart/form-data"])
    fun upload(
        @RequestPart("files") files: List<MultipartFile>,
    ): ResponseEntity<Map<String, Any>> {
        if (files.isEmpty()) throw ProblemException(HttpStatus.BAD_REQUEST, "empty-upload", "파일이 없습니다")
        val accepted =
            ingest.accept(
                files.map { UploadFile(it.originalFilename ?: "unnamed", it.contentType, it.bytes) },
            )
        return ResponseEntity.accepted().body(mapOf("documents" to accepted.map(DocumentResponse::from)))
    }

    @GetMapping
    fun list(): List<DocumentResponse> = ingest.list().map(DocumentResponse::from)

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
    ): DocumentResponse =
        ingest.get(id)?.let(DocumentResponse::from)
            ?: throw ProblemException(HttpStatus.NOT_FOUND, "document-not-found", "문서를 찾을 수 없습니다: $id")

    /** 원본 파일(inline). 브라우저가 열 수 있는 타입(PDF·텍스트)은 그대로 표시되고 나머지는 다운로드된다. */
    @GetMapping("/{id}/content")
    fun content(
        @PathVariable id: UUID,
    ): ResponseEntity<ByteArray> {
        val doc = ingest.get(id) ?: throw ProblemException(HttpStatus.NOT_FOUND, "document-not-found", "문서를 찾을 수 없습니다: $id")
        val file =
            ingest.file(id)
                ?: throw ProblemException(HttpStatus.NOT_FOUND, "file-not-found", "원본 파일이 보관되어 있지 않습니다(원본 보관 기능 이전에 업로드된 문서): $id")
        val mediaType =
            file.contentType?.let { runCatching { MediaType.parseMediaType(it) }.getOrNull() } ?: MediaType.APPLICATION_OCTET_STREAM
        val disposition = ContentDisposition.inline().filename(doc.filename, Charsets.UTF_8).build()
        return ResponseEntity
            .ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
            .contentType(mediaType)
            .body(file.bytes)
    }

    /** 뷰어용 청크 목록(chunkIndex 순). 출처의 chunkIndex 로 해당 위치를 강조한다. */
    @GetMapping("/{id}/chunks")
    fun chunks(
        @PathVariable id: UUID,
    ): List<ChunkResponse> {
        ingest.get(id) ?: throw ProblemException(HttpStatus.NOT_FOUND, "document-not-found", "문서를 찾을 수 없습니다: $id")
        return ingest.chunks(id).map { ChunkResponse(it.chunkIndex, it.page, it.content) }
    }

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        ingest.get(id) ?: throw ProblemException(HttpStatus.NOT_FOUND, "document-not-found", "문서를 찾을 수 없습니다: $id")
        ingest.delete(id)
        return ResponseEntity.noContent().build()
    }
}
