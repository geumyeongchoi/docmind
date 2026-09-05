package dev.gychoi.docmind.integration

import dev.gychoi.docmind.domain.DocumentStatus
import dev.gychoi.docmind.support.FakeAiConfig
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.util.LinkedMultiValueMap
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration

@Tag("integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(FakeAiConfig::class)
class IngestAndChatIntegrationTest {
    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres =
            PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("docmind")
                .withUsername("docmind")
                .withPassword("docmind")
    }

    @LocalServerPort var port: Int = 0

    @Autowired lateinit var rest: TestRestTemplate

    @Autowired lateinit var jdbc: JdbcTemplate

    private fun upload(
        name: String,
        content: String,
    ): Map<*, *> {
        val body = LinkedMultiValueMap<String, Any>()
        body.add(
            "files",
            object : ByteArrayResource(content.toByteArray()) {
                override fun getFilename() = name
            },
        )
        val headers = HttpHeaders().apply { contentType = MediaType.MULTIPART_FORM_DATA }
        val res = rest.postForEntity("/api/documents", HttpEntity(body, headers), Map::class.java)
        res.statusCode shouldBe HttpStatus.ACCEPTED
        @Suppress("UNCHECKED_CAST")
        return (res.body!!["documents"] as List<Map<*, *>>).single()
    }

    private fun awaitDone(id: String): Map<*, *> {
        var last: Map<*, *> = emptyMap<String, Any>()
        await.atMost(Duration.ofSeconds(30)).untilAsserted {
            last = rest.getForObject("/api/documents/$id", Map::class.java)!!
            (last["status"] == DocumentStatus.DONE.name || last["status"] == DocumentStatus.FAILED.name) shouldBe true
        }
        return last
    }

    @Test
    fun `MD 업로드 → DONE → 벡터 row 수 일치 → 질문 시 citations → token → done 순서`() {
        val text =
            """
            # 인사 규정
            연차는 다음 해로 이월할 수 없습니다. 미사용 연차는 소멸합니다.
            재택근무 신청은 최소 3일 전까지 팀장 승인을 받아야 합니다.
            PRC_SOOROU001 프로시저는 레거시 주문 저장을 담당합니다.
            """.trimIndent()
        val accepted = upload("hr-policy.md", text)
        val id = accepted["id"] as String
        val done = awaitDone(id)

        done["status"] shouldBe "DONE"
        val chunkCount = (done["chunkCount"] as Number).toInt()
        chunkCount shouldBeGreaterThan 0
        jdbc.queryForObject("SELECT count(*) FROM docmind_public WHERE metadata->>'documentId' = ?", Long::class.java, id) shouldBe
            chunkCount.toLong()

        // 채팅(SSE)
        val headers =
            HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                accept = listOf(MediaType.TEXT_EVENT_STREAM)
            }
        val sse =
            rest
                .exchange(
                    "/api/chat",
                    HttpMethod.POST,
                    HttpEntity("""{"sessionId":"it-1","question":"연차는 다음 해로 이월할 수 있나요?"}""", headers),
                    String::class.java,
                ).body!!
        val events = Regex("event:(\\w+)").findAll(sse).map { it.groupValues[1] }.toList()

        events.first() shouldBe "citations"
        events.last() shouldBe "done"
        events shouldContain "token"
        sse shouldContain "hr-policy.md"
        sse shouldContain "FAKE_ANSWER"
    }

    @Test
    fun `같은 파일 재업로드는 DUPLICATE 로 즉시 종료`() {
        val content = "중복 테스트 문서입니다. 동일한 내용을 두 번 올립니다."
        val first = upload("dup.md", content)
        awaitDone(first["id"] as String)
        val second = upload("dup-again.md", content)
        second["status"] shouldBe "DUPLICATE"
    }

    @Test
    fun `근거가 없으면 LLM 호출 없이 찾지 못했습니다 응답`() {
        val headers =
            HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                accept = listOf(MediaType.TEXT_EVENT_STREAM)
            }
        val sse =
            rest
                .exchange(
                    "/api/chat",
                    HttpMethod.POST,
                    HttpEntity("""{"sessionId":"it-2","question":"zzqx 존재하지않는 단어 qqq"}""", headers),
                    String::class.java,
                ).body!!
        sse shouldContain "제공된 문서에서 찾지 못했습니다"
        sse shouldContain "\"answered\":false"
    }

    @Test
    fun `health 엔드포인트는 프로파일과 차원을 보고한다`() {
        val h = rest.getForObject("/api/health/ai", Map::class.java)!!
        h["profile"] shouldBe "test"
        h["dimensionsMatch"] shouldBe true
        (h["vectorTable"] as String) shouldStartWith "docmind_"
    }
}
