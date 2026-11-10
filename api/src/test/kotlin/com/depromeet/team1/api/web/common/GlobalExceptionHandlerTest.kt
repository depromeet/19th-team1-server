package com.depromeet.team1.api.web.common

import com.depromeet.team1.core.common.error.AppException
import com.depromeet.team1.core.common.error.CommonError
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** 응답 형식 규약 검사. DB 없이 컨트롤러와 예외 처리기만 띄운다. */
class GlobalExceptionHandlerTest {
    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(SampleController())
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

    @Test
    fun `성공 응답은 data 로 감싼다`() {
        mockMvc
            .perform(get("/sample/ok"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.name").value("sample"))
    }

    @Test
    fun `AppException 은 에러 코드의 분류로 상태 코드를 정한다`() {
        mockMvc
            .perform(get("/sample/not-found"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value(CommonError.RESOURCE_NOT_FOUND.message))
    }

    @Test
    fun `예상하지 못한 예외는 500 으로 응답하고 내부 메시지를 노출하지 않는다`() {
        mockMvc
            .perform(get("/sample/unexpected"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.message").value(CommonError.INTERNAL_ERROR.message))
    }

    @Test
    fun `읽을 수 없는 요청 본문은 400 으로 응답한다`() {
        mockMvc
            .perform(post("/sample/body").contentType(MediaType.APPLICATION_JSON).content("{ broken"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
    }

    @Test
    fun `지원하지 않는 요청 방식은 405 로 응답한다`() {
        mockMvc
            .perform(post("/sample/ok"))
            .andExpect(status().isMethodNotAllowed)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
    }

    @Test
    fun `Spring 이 던지는 그 밖의 예외는 그 예외의 상태 코드를 따른다`() {
        mockMvc
            .perform(post("/sample/body").contentType(MediaType.TEXT_PLAIN).content("text"))
            .andExpect(status().isUnsupportedMediaType)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
    }

    @RestController
    class SampleController {
        @GetMapping("/sample/ok")
        fun ok() = ApiResponse(mapOf("name" to "sample"))

        @GetMapping("/sample/not-found")
        fun notFound(): ApiResponse<Unit> = throw AppException(CommonError.RESOURCE_NOT_FOUND, "sample id=1")

        @GetMapping("/sample/unexpected")
        fun unexpected(): ApiResponse<Unit> = throw IllegalStateException("내부 상태 메시지")

        @PostMapping("/sample/body")
        fun body(@RequestBody body: Map<String, String>) = ApiResponse(body)
    }
}
