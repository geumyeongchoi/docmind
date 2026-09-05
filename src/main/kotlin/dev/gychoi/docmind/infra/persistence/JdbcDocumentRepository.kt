package dev.gychoi.docmind.infra.persistence

import dev.gychoi.docmind.domain.Document
import dev.gychoi.docmind.domain.DocumentRepository
import dev.gychoi.docmind.domain.DocumentStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.util.UUID

@Repository
class JdbcDocumentRepository(
    private val jdbc: JdbcTemplate,
) : DocumentRepository {
    private val mapper =
        RowMapper { rs, _ ->
            Document(
                id = rs.getObject("id", UUID::class.java),
                filename = rs.getString("filename"),
                sha256 = rs.getString("sha256"),
                contentType = rs.getString("content_type"),
                sizeBytes = rs.getLong("size_bytes"),
                status = DocumentStatus.valueOf(rs.getString("status")),
                chunkCount = rs.getInt("chunk_count"),
                elapsedMs = rs.getObject("elapsed_ms")?.let { (it as Number).toLong() },
                error = rs.getString("error"),
                profile = rs.getString("profile"),
                createdAt = rs.getTimestamp("created_at").toInstant(),
                updatedAt = rs.getTimestamp("updated_at").toInstant(),
            )
        }

    override fun save(document: Document): Document {
        jdbc.update(
            """
            INSERT INTO documents (id, filename, sha256, content_type, size_bytes, status, chunk_count, elapsed_ms, error, profile, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            document.id,
            document.filename,
            document.sha256,
            document.contentType,
            document.sizeBytes,
            document.status.name,
            document.chunkCount,
            document.elapsedMs,
            document.error,
            document.profile,
            Timestamp.from(document.createdAt),
            Timestamp.from(document.updatedAt),
        )
        return document
    }

    override fun update(document: Document): Document {
        jdbc.update(
            "UPDATE documents SET status = ?, chunk_count = ?, elapsed_ms = ?, error = ?, updated_at = ? WHERE id = ?",
            document.status.name,
            document.chunkCount,
            document.elapsedMs,
            document.error,
            Timestamp.from(document.updatedAt),
            document.id,
        )
        return document
    }

    override fun findById(id: UUID): Document? = jdbc.query("SELECT * FROM documents WHERE id = ?", mapper, id).firstOrNull()

    override fun findBySha256(
        sha256: String,
        profile: String,
    ): Document? =
        jdbc
            .query(
                "SELECT * FROM documents WHERE sha256 = ? AND profile = ? AND status <> 'DUPLICATE' ORDER BY created_at LIMIT 1",
                mapper,
                sha256,
                profile,
            ).firstOrNull()

    override fun findAll(limit: Int): List<Document> = jdbc.query("SELECT * FROM documents ORDER BY created_at DESC LIMIT ?", mapper, limit)

    override fun delete(id: UUID) {
        jdbc.update("DELETE FROM documents WHERE id = ?", id)
    }
}
