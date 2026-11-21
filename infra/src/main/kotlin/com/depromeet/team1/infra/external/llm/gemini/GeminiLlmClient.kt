package com.depromeet.team1.infra.external.llm.gemini

import com.depromeet.team1.core.common.error.AppException
import com.depromeet.team1.core.llm.LlmClient
import com.depromeet.team1.core.llm.LlmError
import com.depromeet.team1.core.llm.LlmRequest
import com.depromeet.team1.core.llm.LlmResponse
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.net.http.HttpClient

/**
 * Gemini API(generateContent) 텍스트 생성. Gemini 응답 DTO 는 이 파일 밖으로 내보내지 않고 [LlmResponse] 로 바꿔 돌려준다.
 *
 * 프롬프트와 응답 원문은 로그·예외 메시지에 남기지 않는다. 실패는 상태 코드와 예외 종류만 남긴다.
 */
class GeminiLlmClient(private val properties: GeminiProperties) : LlmClient {
    private val restClient: RestClient = RestClient.builder()
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.connectTimeout).build(),
            ).apply { setReadTimeout(properties.readTimeout) },
        )
        .build()

    override fun generate(request: LlmRequest): LlmResponse {
        // local 에서는 키 없이도 뜬다(GeminiConfiguration). 이때는 Gemini 를 부르지 않고 실패시킨다.
        if (!properties.isConfigured()) throw unavailable("키 없음")
        val response = call {
            restClient.post()
                .uri("${properties.baseUrl}/v1beta/models/{model}:generateContent", properties.model)
                .header(API_KEY_HEADER, properties.apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(GeminiRequest.of(request))
                .retrieve()
                .body(GeminiResponse::class.java)
        } ?: throw unavailable("빈 응답")
        return response.toLlmResponse(properties.model) ?: throw unavailable("텍스트·사용량 없음")
    }

    /**
     * Gemini 호출의 오류를 내부 예외로 바꾼다. 호출 한도 초과(429)는 다른 오류와 구분한다.
     * 원인 예외는 붙이지 않는다. 원인의 메시지에 Gemini 응답 본문이 들어 있어 에러 로그에 그대로 찍히기 때문이다.
     */
    private fun <T> call(block: () -> T): T = try {
        block()
    } catch (e: RestClientResponseException) {
        val status = e.statusCode.value()
        if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
            log.warn("Gemini 호출 한도 초과: status={}", status)
            throw AppException(LlmError.LLM_RATE_LIMITED, "gemini status=$status")
        }
        throw unavailable("status=$status ${e.javaClass.simpleName}")
    } catch (e: RestClientException) {
        throw unavailable(e.javaClass.simpleName)
    }

    private fun unavailable(reason: String): AppException {
        log.warn("Gemini 호출 실패: {}", reason)
        return AppException(LlmError.LLM_UNAVAILABLE, "gemini $reason")
    }

    companion object {
        private const val API_KEY_HEADER = "x-goog-api-key"
        private val log = LoggerFactory.getLogger(GeminiLlmClient::class.java)
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
private data class GeminiRequest(val systemInstruction: GeminiContent?, val contents: List<GeminiContent>) {
    companion object {
        fun of(request: LlmRequest) = GeminiRequest(
            systemInstruction = request.systemInstruction?.let {
                GeminiContent(role = null, parts = listOf(GeminiPart(it)))
            },
            contents = listOf(GeminiContent(role = "user", parts = listOf(GeminiPart(request.prompt)))),
        )
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
private data class GeminiContent(val role: String?, val parts: List<GeminiPart>?)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class GeminiPart(val text: String?)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class GeminiResponse(
    val candidates: List<GeminiCandidate>?,
    val usageMetadata: GeminiUsage?,
    val modelVersion: String?,
) {
    /**
     * 첫 후보의 텍스트 조각을 이어 붙인다. 텍스트가 없어도(안전 필터 등) 사용량이 있으면 빈 텍스트로 돌려줘 과금된 토큰이 기록되게 한다.
     * 텍스트도 사용량도 없으면 null.
     */
    fun toLlmResponse(requestedModel: String): LlmResponse? {
        val text = candidates?.firstOrNull()?.content?.parts?.mapNotNull { it.text }?.joinToString("").orEmpty()
        if (text.isEmpty() && usageMetadata == null) return null
        return LlmResponse(
            text = text,
            model = (modelVersion?.takeIf { it.isNotBlank() } ?: requestedModel).take(MODEL_MAX),
            inputTokens = usageMetadata?.promptTokenCount ?: 0,
            // 생각 토큰도 출력 요금으로 과금되므로 출력 토큰에 넣는다.
            outputTokens = (usageMetadata?.candidatesTokenCount ?: 0) + (usageMetadata?.thoughtsTokenCount ?: 0),
        )
    }

    companion object {
        private const val MODEL_MAX = 50
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class GeminiCandidate(val content: GeminiContent?)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class GeminiUsage(val promptTokenCount: Int?, val candidatesTokenCount: Int?, val thoughtsTokenCount: Int?)
