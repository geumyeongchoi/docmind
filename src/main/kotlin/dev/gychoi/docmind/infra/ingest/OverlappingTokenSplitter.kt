package dev.gychoi.docmind.infra.ingest

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingType
import com.knuddels.jtokkit.api.IntArrayList

/**
 * 토큰 기준 고정 크기 + 오버랩 분할기.
 * Spring AI TokenTextSplitter는 오버랩을 지원하지 않아 직접 구현(설계 문서 §6-1).
 *
 * 규칙
 *  - 청크는 최대 chunkSizeTokens 토큰. 마지막 청크가 아니면 문장 종결 부호 뒤로 끝을 당긴다(절반 이상 채워진 경우만).
 *  - 다음 청크 시작 = (실제로 사용한 토큰 수 - overlapTokens). 문장 경계로 잘라 짧아진 만큼 시작점도 앞당겨지므로
 *    텍스트가 유실되지 않고, 인접 청크는 항상 overlapTokens 만큼 겹친다.
 */
class OverlappingTokenSplitter(
    private val chunkSizeTokens: Int,
    private val overlapTokens: Int,
    private val encoding: Encoding = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE),
) {
    init {
        require(chunkSizeTokens > 0) { "chunkSizeTokens must be > 0" }
        require(overlapTokens in 0 until chunkSizeTokens) { "overlap must be in [0, chunkSize)" }
    }

    fun split(text: String): List<String> {
        val normalized = text.replace("\r\n", "\n").trim()
        if (normalized.isEmpty()) return emptyList()
        val tokens = encoding.encode(normalized)
        val total = tokens.size()
        if (total <= chunkSizeTokens) return listOf(normalized)

        val out = ArrayList<String>()
        var start = 0
        while (start < total) {
            val end = minOf(start + chunkSizeTokens, total)
            var usedTokens = end - start
            var piece = encoding.decode(tokens.slice(start, end))
            if (end < total) {
                val cut = lastSentenceBoundary(piece)
                if (cut >= piece.length / 2) {
                    piece = piece.substring(0, cut)
                    usedTokens = encoding.countTokens(piece).coerceAtLeast(1)
                }
            }
            val trimmed = piece.trim()
            if (trimmed.isNotEmpty()) out += trimmed
            if (end >= total) break
            start += maxOf(usedTokens - overlapTokens, 1)
        }
        return out
    }

    private fun lastSentenceBoundary(s: String): Int {
        val candidates = listOf(". ", "다.\n", "다. ", "\n\n", "? ", "! ", ".\n")
        val idx = candidates.maxOf { s.lastIndexOf(it) }
        return if (idx < 0) -1 else idx + 2
    }

    fun countTokens(text: String): Int = encoding.countTokens(text)

    private fun IntArrayList.slice(
        from: Int,
        to: Int,
    ): IntArrayList {
        val r = IntArrayList(to - from)
        for (i in from until to) r.add(this.get(i))
        return r
    }
}
