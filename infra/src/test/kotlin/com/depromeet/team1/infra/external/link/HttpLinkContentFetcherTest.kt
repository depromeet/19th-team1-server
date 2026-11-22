package com.depromeet.team1.infra.external.link

import com.depromeet.team1.core.item.CollectLevel
import com.depromeet.team1.core.item.FetchMiss
import com.depromeet.team1.core.item.Platform
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HttpLinkContentFetcherTest {
    private val youtube = MockWebServer()
    private lateinit var fetcher: HttpLinkContentFetcher

    @BeforeEach
    fun setUp() {
        youtube.start()
        fetcher = HttpLinkContentFetcher(
            LinkFetchProperties(youtubeOembedUrl = youtube.url("/oembed").toString()),
        )
    }

    @AfterEach
    fun tearDown() {
        youtube.close()
    }

    @Test
    fun `유튜브 영상 주소면 oEmbed로 받은 제목·채널·썸네일을 돌려준다`() {
        youtube.enqueue(json(200, OEMBED_BODY))

        val result = fetcher.fetch("https://www.youtube.com/watch?v=abc123def45")

        assertEquals(Platform.YOUTUBE, result.platform)
        assertEquals(CollectLevel.AUTO, result.collectLevel)
        assertEquals("영상 제목", result.title)
        assertEquals("채널 이름", result.author)
        assertEquals("https://i.ytimg.com/vi/abc123def45/hqdefault.jpg", result.ogImageUrl)
    }

    @Test
    fun `유튜브 주소 형태가 달라도 같은 영상으로 oEmbed에 요청한다`() {
        val urls = listOf(
            "https://www.youtube.com/watch?v=abc123def45",
            "https://youtu.be/abc123def45?si=share",
            "https://www.youtube.com/shorts/abc123def45",
            "https://www.youtube.com/live/abc123def45",
            "https://www.youtube.com/embed/abc123def45",
            "https://m.youtube.com/watch?v=abc123def45&t=30",
        )

        for (url in urls) {
            youtube.enqueue(json(200, OEMBED_BODY))
            fetcher.fetch(url)

            val requested = youtube.takeRequest().url
            assertEquals("https://www.youtube.com/watch?v=abc123def45", requested.queryParameter("url"), url)
            assertEquals("json", requested.queryParameter("format"), url)
        }
    }

    @Test
    fun `요청하지 않는 곳은 요청 없이 '요청 안 함' 누락과 최종 주소를 돌려준다`() {
        val url = "https://www.musinsa.com/products/1234567"

        val result = fetcher.fetch(url)

        assertEquals(Platform.OTHER, result.platform)
        assertEquals(CollectLevel.LINK_MEMO, result.collectLevel)
        assertEquals(url, result.finalUrl)
        assertEquals(FetchMiss.NOT_REQUESTED, result.miss)
        assertFalse(FetchMiss.NOT_REQUESTED.retryable)
        assertEquals(0, youtube.requestCount)
    }

    private fun json(code: Int, body: String): MockResponse = MockResponse.Builder()
        .code(code)
        .addHeader("Content-Type", "application/json;charset=UTF-8")
        .body(body)
        .build()

    companion object {
        private val OEMBED_BODY = """
            {
              "title": "영상 제목",
              "author_name": "채널 이름",
              "thumbnail_url": "https://i.ytimg.com/vi/abc123def45/hqdefault.jpg"
            }
        """.trimIndent()
    }
}
