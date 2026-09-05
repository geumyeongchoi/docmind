package dev.gychoi.docmind.api

import dev.gychoi.docmind.application.IngestDocumentUseCase
import dev.gychoi.docmind.application.UploadFile
import dev.gychoi.docmind.domain.Document
import dev.gychoi.docmind.domain.DocumentStatus
import org.springframework.http.HttpStatus
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

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        ingest.get(id) ?: throw ProblemException(HttpStatus.NOT_FOUND, "document-not-found", "문서를 찾을 수 없습니다: $id")
        ingest.delete(id)
        return ResponseEntity.noContent().build()
    }
}
