package dev.gychoi.docmind.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "docmind")
data class DocmindProperties(
    val profileLabel: String = "default",
    val ingest: Ingest = Ingest(),
    val retrieval: Retrieval = Retrieval(),
    val chat: Chat = Chat(),
    val safeguard: Safeguard = Safeguard(),
) {
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
        val sensitivePhrases: List<String> = emptyList(),
    )
}
