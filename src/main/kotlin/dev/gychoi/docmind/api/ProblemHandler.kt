package dev.gychoi.docmind.api

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException
import java.net.URI

class ProblemException(
    val status: HttpStatus,
    val type: String,
    message: String,
) : RuntimeException(message)

/** RFC 9457 application/problem+json (설계 문서 §8) */
@RestControllerAdvice
class ProblemHandler {
    @ExceptionHandler(ProblemException::class)
    fun problem(e: ProblemException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(e.status, e.message).apply { type = URI.create("urn:docmind:${e.type}") }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalid(e: MethodArgumentNotValidException): ProblemDetail =
        ProblemDetail
            .forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                e.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" },
            ).apply { type = URI.create("urn:docmind:validation") }

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun tooLarge(e: MaxUploadSizeExceededException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "업로드 용량 제한을 초과했습니다").apply {
            type =
                URI.create("urn:docmind:too-large")
        }
}
