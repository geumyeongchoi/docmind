package dev.gychoi.docmind.eval

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class GoldenItem(
    val id: String,
    val question: String,
    val keywords: List<String>,
    val expectedDoc: String? = null,
    val tag: String? = null,
)

data class EvalRow(
    val id: String,
    val answerHit: Boolean,
    val citationHit: Boolean?,
    val latencyMs: Long,
    val answer: String,
    val topFiles: List<String>,
)

/**
 * 평가 러너 — 기동 중인 docmind에 HTTP로 질의하고 정답 포함률 / 인용 정확률 / p50 을 계산한다.
 *   ./gradlew eval  [-Peval.baseUrl=http://localhost:8080]
 * 앱과 분리한 이유: 같은 코드로 private/public 배포 어느 쪽이든 겨눌 수 있고, 프로덕션 이미지에 평가 의존성을 넣지 않기 위해.
 */
fun main(args: Array<String>) {
    val baseUrl = args.getOrElse(0) { "http://localhost:8080" }
    val goldenPath = args.getOrElse(1) { "eval/golden.yaml" }
    val reportPath = args.getOrElse(2) { "build/eval/report.md" }

    val yaml = ObjectMapper(YAMLFactory()).registerKotlinModule().findAndRegisterModules()
    val items: List<GoldenItem> = yaml.readValue(File(goldenPath))
    val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    val json = ObjectMapper().registerKotlinModule()

    val health =
        client
            .send(
                HttpRequest.newBuilder(URI.create("$baseUrl/api/health/ai")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            ).body()
    println("target: $baseUrl  health: $health")

    val rows =
        items.map { item ->
            val started = System.currentTimeMillis()
            val body = json.writeValueAsString(mapOf("sessionId" to "eval-${item.id}-${System.nanoTime()}", "question" to item.question))
            val req =
                HttpRequest
                    .newBuilder(URI.create("$baseUrl/api/chat"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .timeout(Duration.ofSeconds(180))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build()
            val sse = client.send(req, HttpResponse.BodyHandlers.ofString()).body()
            val latency = System.currentTimeMillis() - started

            val (answer, files) = parseSse(sse, json)
            val norm = answer.lowercase().replace(Regex("\\s+"), "")
            val answerHit = item.keywords.all { norm.contains(it.lowercase().replace(Regex("\\s+"), "")) }
            val citationHit = item.expectedDoc?.let { exp -> files.any { it.equals(exp, ignoreCase = true) } }
            println(
                "${item.id}  answer=${if (answerHit) "O" else "X"}  citation=${citationHit?.let {
                    if (it) "O" else "X"
                } ?: "-"}  ${latency}ms",
            )
            EvalRow(item.id, answerHit, citationHit, latency, answer, files)
        }

    val answerRate = rows.count { it.answerHit } * 100.0 / rows.size
    val cited = rows.filter { it.citationHit != null }
    val citationRate = if (cited.isEmpty()) 0.0 else cited.count { it.citationHit == true } * 100.0 / cited.size
    val p50 = rows.map { it.latencyMs }.sorted().let { it[it.size / 2] }

    val report =
        buildString {
            appendLine("# docmind 평가 리포트 — ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}")
            appendLine()
            appendLine("- target: `$baseUrl`")
            appendLine("- health: `$health`")
            appendLine()
            appendLine("| 지표 | 값 |")
            appendLine("|---|---|")
            appendLine("| 문항 수 | ${rows.size} |")
            appendLine("| 정답 포함률 | ${"%.1f".format(answerRate)}% |")
            appendLine("| 인용 정확률 | ${"%.1f".format(citationRate)}% (${cited.size}문항) |")
            appendLine("| p50 지연 | $p50 ms |")
            appendLine()
            appendLine("| id | 정답 | 인용 | ms | top 파일 | 답변(앞 80자) |")
            appendLine("|---|---|---|---|---|---|")
            rows.forEach {
                appendLine(
                    "| ${it.id} | ${if (it.answerHit) "O" else "X"} | ${it.citationHit?.let { c ->
                        if (c) "O" else "X"
                    } ?: "-"} | ${it.latencyMs} | ${it.topFiles.take(
                        3,
                    ).joinToString(", ")} | ${it.answer.replace("|", "\\|").replace("\n", " ").take(80)} |",
                )
            }
        }
    File(reportPath).apply { parentFile.mkdirs() }.writeText(report)
    println()
    println(report)
}

/** SSE 본문에서 token 이벤트를 이어붙여 답변을, citations 이벤트에서 파일명 목록을 얻는다. */
internal fun parseSse(
    sse: String,
    json: ObjectMapper,
): Pair<String, List<String>> {
    val answer = StringBuilder()
    val files = ArrayList<String>()
    var event = ""
    for (raw in sse.lines()) {
        val line = raw.trimEnd()
        when {
            line.startsWith("event:") -> event = line.removePrefix("event:").trim()
            line.startsWith("data:") -> {
                val data = line.removePrefix("data:").trim()
                when (event) {
                    "token" -> answer.append(json.readTree(data).path("text").asText(""))
                    "citations" -> json.readTree(data).forEach { files += it.path("filename").asText() }
                }
            }
        }
    }
    return answer.toString() to files.distinct()
}
