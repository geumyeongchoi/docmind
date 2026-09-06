package dev.gychoi.docmind.application

import dev.gychoi.docmind.config.DocmindProperties
import dev.gychoi.docmind.domain.Chunk
import dev.gychoi.docmind.domain.ChunkStore
import dev.gychoi.docmind.domain.Document
import dev.gychoi.docmind.domain.DocumentFileStore
import dev.gychoi.docmind.domain.DocumentParser
import dev.gychoi.docmind.domain.DocumentRepository
import dev.gychoi.docmind.domain.DocumentStatus
import dev.gychoi.docmind.domain.StoredFile
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.util.UUID

data class UploadFile(
    val filename: String,
    val contentType: String?,
    val bytes: ByteArray,
)

@Service
class IngestDocumentUseCase(
    private val documents: DocumentRepository,
    private val parser: DocumentParser,
    private val chunkStore: ChunkStore,
    private val fileStore: DocumentFileStore,
    private val props: DocmindProperties,
    private val worker: IngestWorker,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 업로드 수락: 중복이면 DUPLICATE로 즉시 종료, 아니면 QUEUED 저장 후 비동기 처리. */
    fun accept(files: List<UploadFile>): List<Document> =
        files.map { file ->
            val sha = sha256(file.bytes)
            val existing = documents.findBySha256(sha, props.effectiveIndexLabel)
            if (existing != null) {
                log.info("duplicate upload skipped: {} (same as {})", file.filename, existing.id)
                // 원본 보관 기능 이전에 올린 문서는 같은 파일을 다시 올리면 원본만 채워 넣는다(재인제스트 없이 뷰어 활성화)
                if (fileStore.load(existing.id) == null) fileStore.save(existing.id, file.contentType, file.bytes)
                documents.save(Document.duplicateOf(existing, file.filename))
            } else {
                val doc =
                    documents.save(
                        Document.queued(file.filename, sha, file.contentType, file.bytes.size.toLong(), props.effectiveIndexLabel),
                    )
                fileStore.save(doc.id, file.contentType, file.bytes) // 출처 → 원문 이동용 원본 보관
                worker.process(doc.id, file)
                doc
            }
        }

    fun get(id: UUID): Document? = documents.findById(id)

    fun list(): List<Document> = documents.findAll()

    /** 원본 파일. DUPLICATE 문서는 원본(같은 sha256)의 파일을 돌려준다. */
    fun file(id: UUID): StoredFile? {
        val doc = documents.findById(id) ?: return null
        val originalId = if (doc.status == DocumentStatus.DUPLICATE) documents.findBySha256(doc.sha256, doc.profile)?.id ?: id else id
        return fileStore.load(originalId)
    }

    fun chunks(id: UUID): List<Chunk> = chunkStore.listByDocument(id)

    fun delete(id: UUID) {
        chunkStore.deleteByDocument(id)
        documents.delete(id)
    }

    companion object {
        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

/**
 * 비동기 인제스트 워커. 별도 빈이어야 @Async 프록시가 동작한다(self-invocation 금지).
 * 상태머신: QUEUED → PROCESSING → DONE | FAILED. 실패 시 부분 적재 청크는 삭제한다.
 */
@Service
class IngestWorker(
    private val documents: DocumentRepository,
    private val parser: DocumentParser,
    private val chunkStore: ChunkStore,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    fun process(
        documentId: UUID,
        file: UploadFile,
    ) {
        val started = System.currentTimeMillis()
        val doc = documents.findById(documentId) ?: return
        documents.update(doc.processing())
        try {
            val drafts = parser.parse(file.bytes, file.filename, file.contentType)
            require(drafts.isNotEmpty()) { "문서에서 텍스트를 추출하지 못했습니다" }
            val count = chunkStore.addChunks(doc, drafts)
            val elapsed = System.currentTimeMillis() - started
            documents.update(doc.done(count, elapsed))
            log.info("ingested {} → {} chunks in {} ms", file.filename, count, elapsed)
        } catch (e: Exception) {
            log.warn("ingest failed for {}: {}", file.filename, e.toString())
            runCatching { chunkStore.deleteByDocument(documentId) }
            documents.update(doc.failed(e.message ?: e.javaClass.simpleName, System.currentTimeMillis() - started))
        }
    }
}
