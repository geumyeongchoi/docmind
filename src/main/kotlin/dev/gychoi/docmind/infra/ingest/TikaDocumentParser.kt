package dev.gychoi.docmind.infra.ingest

import dev.gychoi.docmind.config.DocmindProperties
import dev.gychoi.docmind.domain.ChunkDraft
import dev.gychoi.docmind.domain.DocumentParser
import org.springframework.ai.reader.tika.TikaDocumentReader
import org.springframework.core.io.ByteArrayResource
import org.springframework.stereotype.Component

/**
 * Tika로 텍스트 추출(PDF·DOCX·MD·TXT·HTML) → 오버랩 토큰 분할.
 * Tika는 전체 문서를 하나의 Document로 돌려주므로 페이지는 form-feed(\f) 구분자가 있을 때만 추정한다.
 */
@Component
class TikaDocumentParser(
    props: DocmindProperties,
) : DocumentParser {
    private val splitter = OverlappingTokenSplitter(props.ingest.chunkSizeTokens, props.ingest.chunkOverlapTokens)

    override fun parse(
        bytes: ByteArray,
        filename: String,
        contentType: String?,
    ): List<ChunkDraft> {
        val resource =
            object : ByteArrayResource(bytes) {
                override fun getFilename() = filename
            }
        val text =
            if (isPlainText(filename, contentType)) {
                String(bytes, Charsets.UTF_8)
            } else {
                TikaDocumentReader(resource).read().joinToString("\n") { it.text ?: "" }
            }
        val pages = text.split('\u000C').filter { it.isNotBlank() }
        val drafts = ArrayList<ChunkDraft>()
        var index = 0
        pages.forEachIndexed { p, pageText ->
            for (piece in splitter.split(pageText)) {
                drafts += ChunkDraft(piece, page = if (pages.size > 1) p + 1 else null, chunkIndex = index++)
            }
        }
        return drafts
    }

    private fun isPlainText(
        filename: String,
        contentType: String?,
    ): Boolean {
        val lower = filename.lowercase()
        return lower.endsWith(".md") || lower.endsWith(".txt") || lower.endsWith(".markdown") ||
            contentType == "text/plain" || contentType == "text/markdown"
    }
}
