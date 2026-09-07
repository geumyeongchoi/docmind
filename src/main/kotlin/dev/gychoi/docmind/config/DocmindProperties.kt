package dev.gychoi.docmind.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "docmind")
data class DocmindProperties(
    /** 실행 모드 라벨(private/public/claude/test). 화면·응답 메타에 표시된다. */
    val profileLabel: String = "default",
    /** 문서가 속한 임베딩 공간 라벨. 생략하면 profileLabel. 같은 임베딩 모델을 쓰는 프로파일끼리 문서를 공유할 때 지정(예: claude → private). */
    val indexLabel: String? = null,
    val ingest: Ingest = Ingest(),
    val retrieval: Retrieval = Retrieval(),
    val chat: Chat = Chat(),
    val safeguard: Safeguard = Safeguard(),
) {
    val effectiveIndexLabel: String get() = indexLabel ?: profileLabel

    data class Ingest(
        val chunkSizeTokens: Int = 600,
        val chunkOverlapTokens: Int = 80,
        val embeddingBatchSize: Int = 32,
    )

    data class Retrieval(
        val topK: Int = 6,
        val similarityThreshold: Double = 0.35,
        val hybrid: Boolean = true,
        val rrfK: Int = 60,
    )

    data class Chat(
        val maxMemoryMessages: Int = 20,
    )

    data class Safeguard(
        /** 질문·답변 어디에 나와도 차단하는 문구. 문서 안의 지시문 판정에도 함께 쓰인다. */
        val sensitivePhrases: List<String> = emptyList(),
        /**
         * 출력 후처리에서 "지시문 복창"으로 볼 최소 일치 길이(정규화 후 글자 수).
         * 낮추면 오탐이 늘고, 높이면 짧은 지시문을 놓친다. 12자 ≈ 한글 12음절.
         */
        val echoLength: Int = 12,
    )
}
