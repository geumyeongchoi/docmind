package dev.gychoi.docmind.mcp

import dev.gychoi.docmind.application.AskQuestionUseCase
import dev.gychoi.docmind.domain.AnswerEvent
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

data class SearchHit(
    val documentId: String?,
    val filename: String,
    val page: Int?,
    val score: Double?,
    val snippet: String,
)

data class AskResult(
    val answer: String,
    val citations: List<SearchHit>,
)

/**
 * MCP 서버 툴 — Claude Desktop / Claude Code가 docmind 검색을 도구로 호출한다.
 * 엔드포인트: SSE /sse, 메시지 /mcp/message (spring.ai.mcp.server.* 참고)
 */
@Component
class DocmindMcpTools(
    private val ask: AskQuestionUseCase,
) {
    @Tool(name = "search_docs", description = "사내 문서에서 질의와 관련된 청크를 검색합니다. 근거를 직접 확인하고 싶을 때 사용합니다.")
    fun searchDocs(
        @ToolParam(description = "검색 질의(자연어 또는 키워드)") query: String,
        @ToolParam(description = "반환 개수(기본 6)", required = false) topK: Int?,
    ): List<SearchHit> =
        ask.search(query).take(topK ?: 6).map {
            SearchHit(it.documentId?.toString(), it.filename, it.page, it.score, it.content.take(500))
        }

    @Tool(name = "ask_docs", description = "사내 문서를 근거로 질문에 답합니다. 근거가 없으면 '제공된 문서에서 찾지 못했습니다.'를 반환합니다.")
    fun askDocs(
        @ToolParam(description = "질문") question: String,
    ): AskResult {
        val sb = StringBuilder()
        var hits: List<SearchHit> = emptyList()
        ask.ask("mcp-${System.nanoTime()}", question) { ev ->
            when (ev) {
                is AnswerEvent.Citations ->
                    hits =
                        ev.items.map { SearchHit(it.documentId?.toString(), it.filename, it.page, it.score, it.snippet) }
                is AnswerEvent.Token -> sb.append(ev.text)
                is AnswerEvent.Done -> Unit
            }
        }
        return AskResult(sb.toString(), hits)
    }
}

@Configuration
class McpToolsConfig {
    @Bean
    fun docmindToolCallbacks(tools: DocmindMcpTools): ToolCallbackProvider = MethodToolCallbackProvider.builder().toolObjects(tools).build()
}
