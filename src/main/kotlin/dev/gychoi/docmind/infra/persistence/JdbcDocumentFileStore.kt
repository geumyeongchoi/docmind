package dev.gychoi.docmind.infra.persistence

import dev.gychoi.docmind.domain.DocumentFileStore
import dev.gychoi.docmind.domain.StoredFile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

/** 원본 파일을 document_files(BYTEA)에 보관. 문서 삭제 시 FK CASCADE 로 함께 삭제된다(V4). */
@Repository
class JdbcDocumentFileStore(
    private val jdbc: JdbcTemplate,
) : DocumentFileStore {
    override fun save(
        documentId: UUID,
        contentType: String?,
        bytes: ByteArray,
    ) {
        jdbc.update("INSERT INTO document_files (document_id, content_type, bytes) VALUES (?, ?, ?)", documentId, contentType, bytes)
    }

    override fun load(documentId: UUID): StoredFile? =
        jdbc
            .query(
                "SELECT content_type, bytes FROM document_files WHERE document_id = ?",
                { rs, _ -> StoredFile(rs.getString("content_type"), rs.getBytes("bytes")) },
                documentId,
            ).firstOrNull()
}
