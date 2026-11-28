package com.depromeet.team1.infra.external.link

import com.depromeet.team1.core.common.error.AppException
import com.depromeet.team1.core.item.CollectLevel
import com.depromeet.team1.core.item.FetchMiss
import com.depromeet.team1.core.item.LinkError
import com.depromeet.team1.core.item.Platform
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.apache.hc.client5.http.DnsResolver
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.Charset
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HttpLinkContentFetcherTest {
    private val youtube = MockWebServer()
    private val web = MockWebServer()
    private lateinit var fetcher: HttpLinkContentFetcher

    @BeforeEach
    fun setUp() {
        youtube.start()
        web.start()
        fetcher = HttpLinkContentFetcher(
            LinkFetchProperties(youtubeOembedUrl = youtube.url("/oembed").toString()),
            dnsResolver = AllHostsToLoopback,
        )
    }

    @AfterEach
    fun tearDown() {
        youtube.close()
        web.close()
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
            "https://youtube.com/watch?v=abc123def45",
            "https://music.youtube.com/watch?v=abc123def45",
            "https://WWW.YouTube.com/watch?v=abc123def45",
            "https://www.youtube-nocookie.com/embed/abc123def45",
            "https://www.youtube.com/v/abc123def45",
            "http://www.youtube.com/v/abc123def45&hl=en_US&fs=1",
        )

        for (url in urls) {
            youtube.enqueue(json(200, OEMBED_BODY))
            fetcher.fetch(url)

            val requested = assertNotNull(youtube.takeRequest(1, TimeUnit.SECONDS), url).url
            assertEquals("https://www.youtube.com/watch?v=abc123def45", requested.queryParameter("url"), url)
            assertEquals("json", requested.queryParameter("format"), url)
        }
    }

    @Test
    fun `유튜브인데 영상 주소가 아니면 요청하지 않고 '요청 안 함' 누락을 돌려준다`() {
        val urls = listOf(
            "https://www.youtube.com/@channel",
            "https://www.youtube.com/playlist?list=PLabc",
            "https://www.youtube.com/watch",
            "https://www.youtube.com/watch?v=",
            "https://www.youtube.com/watch?v=short",
            "https://www.youtube.com/embed/videoseries?list=PLabc",
            "https://www.youtube.com/embed/live_stream?channel=UCabc",
        )

        for (url in urls) {
            val result = fetcher.fetch(url)

            assertEquals(Platform.YOUTUBE, result.platform, url)
            assertEquals(CollectLevel.LINK_MEMO, result.collectLevel, url)
            assertEquals(url, result.finalUrl, url)
            assertEquals(FetchMiss.NOT_REQUESTED, result.miss, url)
        }
        assertEquals(0, youtube.requestCount)
    }

    @Test
    fun `유튜브 oEmbed 오류 응답도 웹 페이지와 같은 예외로 바꾼다`() {
        val expected = mapOf(
            404 to LinkError.LINK_NOT_FOUND,
            401 to LinkError.LINK_BLOCKED,
            403 to LinkError.LINK_BLOCKED,
            429 to LinkError.LINK_RATE_LIMITED,
            500 to LinkError.LINK_UNAVAILABLE,
        )

        for ((code, error) in expected) {
            youtube.enqueue(json(code, "{}"))

            val thrown = assertFailsWith<AppException>("$code") { fetcher.fetch("https://youtu.be/abc123def45") }

            assertEquals(error, thrown.errorCode, "$code")
        }
    }

    @Test
    fun `유튜브 oEmbed가 제때 응답하지 않으면 '받을 수 없음' 예외를 던진다`() {
        val quickFetcher = HttpLinkContentFetcher(
            LinkFetchProperties(
                youtubeOembedUrl = youtube.url("/oembed").toString(),
                readTimeout = Duration.ofMillis(200),
            ),
            dnsResolver = AllHostsToLoopback,
        )
        youtube.enqueue(json(200, OEMBED_BODY).newBuilder().headersDelay(2, TimeUnit.SECONDS).build())

        val thrown = assertFailsWith<AppException> { quickFetcher.fetch("https://youtu.be/abc123def45") }

        assertEquals(LinkError.LINK_UNAVAILABLE, thrown.errorCode)
    }

    @Test
    fun `요청하지 않는 곳은 '요청 안 함' 누락과 최종 주소를 돌려준다`() {
        val url = "https://www.musinsa.com/products/1234567"

        val result = fetcher.fetch(url)

        assertEquals(Platform.OTHER, result.platform)
        assertEquals(CollectLevel.LINK_MEMO, result.collectLevel)
        assertEquals(url, result.finalUrl)
        assertEquals(FetchMiss.NOT_REQUESTED, result.miss)
    }

    @Test
    fun `요청하지 않는 곳은 대소문자·끝의 점·하위 도메인이 달라도 요청하지 않는다`() {
        val urls = listOf(
            "http://WWW.MUSINSA.COM:${web.port}/products/1234567",
            "http://www.musinsa.com.:${web.port}/products/1234567",
            "http://musinsa.com:${web.port}/products/1234567",
            "http://m.musinsa.com:${web.port}/products/1234567",
        )
        repeat(urls.size) { web.enqueue(html(200, ARTICLE_HTML)) }

        for (url in urls) {
            assertEquals(FetchMiss.NOT_REQUESTED, fetcher.fetch(url).miss, url)
        }
        assertEquals(0, web.requestCount)
    }

    @Test
    fun `SNS·쇼핑몰·지도·네이버 뉴스는 요청하지 않는다`() {
        val paths = listOf(
            "www.instagram.com/p/abc",
            "x.com/someone/status/1",
            "twitter.com/someone/status/1",
            "www.pinterest.com/pin/1",
            "kr.pinterest.com/pin/1",
            "www.pinterest.co.kr/pin/1",
            "place.map.kakao.com/1",
            "map.kakao.com/?itemId=1",
            "gift.kakao.com/product/1",
            "www.coupang.com/vp/products/1",
            "smartstore.naver.com/store/products/1",
            "www.musinsa.com/products/1",
            "www.29cm.co.kr/products/1",
            "ohou.se/productions/1",
            "n.news.naver.com/article/001/0000000001",
            "map.naver.com/p/entry/place/1",
        )
        repeat(paths.size) { web.enqueue(html(200, ARTICLE_HTML)) }

        for (path in paths) {
            val result = fetcher.fetch(localUrl(path))

            assertEquals(FetchMiss.NOT_REQUESTED, result.miss, path)
        }
        assertEquals(0, web.requestCount)
    }

    @Test
    fun `웹 페이지면 og 태그의 제목·설명·대표 이미지와 본문을 돌려준다`() {
        web.enqueue(html(200, ARTICLE_HTML))
        val url = web.url("/post/1").toString()

        val result = fetcher.fetch(url)

        assertEquals(Platform.OTHER, result.platform)
        assertEquals(CollectLevel.AUTO, result.collectLevel)
        assertEquals(url, result.finalUrl)
        assertEquals("글 제목", result.title)
        assertEquals("글 설명", result.description)
        assertEquals("https://img.example.com/cover.jpg", result.ogImageUrl)
        assertEquals("첫 문단입니다. 둘째 문단입니다.", result.body)
        assertNull(result.miss)
    }

    @Test
    fun `블로그 호스트의 글이면 플랫폼은 BLOG다`() {
        val paths = listOf(
            "blog.naver.com/someone/223000000000",
            "m.blog.naver.com/someone/223000000000",
            "someone.tistory.com/12",
            "velog.io/@someone/post-1",
            "brunch.co.kr/@someone/3",
            "medium.com/@someone/post-1",
        )

        for (path in paths) {
            web.enqueue(html(200, ARTICLE_HTML))
            val result = fetcher.fetch(localUrl(path))

            assertEquals(Platform.BLOG, result.platform, path)
            assertEquals(CollectLevel.AUTO, result.collectLevel, path)
        }
    }

    @Test
    fun `목록에 없는 호스트라도 JSON-LD 최상위 타입이 블로그 글이나 블로그면 플랫폼은 BLOG다`() {
        val jsonLds = listOf(
            """{"@context":"https://schema.org","@type":["Article","BlogPosting"]}""",
            """{"@context":"https://schema.org","@type":"Blog"}""",
            """{"@type":"http://schema.org/BlogPosting"}""",
            """{"@context":"https://schema.org","@graph":[{"@type":"WebPage"},{"@type":"BlogPosting"}]}""",
            """{"@context":"https://schema.org","@graph":{"@type":"BlogPosting"}}""",
        )

        for (jsonLd in jsonLds) {
            web.enqueue(html(200, pageWithJsonLd(jsonLd)))
            val result = fetcher.fetch("http://myblog.example:${web.port}/2026/11/post")

            assertEquals(Platform.BLOG, result.platform, jsonLd)
        }
    }

    @Test
    fun `엄격한 JSON이 아닌 JSON-LD도 타입을 읽는다`() {
        val jsonLds = listOf(
            "{\"@type\":\"BlogPosting\",\"description\":\"첫 줄\n둘째 줄\"}",
            """{"@type":"BlogPosting","headline":"제목",}""",
            """/* <![CDATA[ */ {"@type":"BlogPosting"} /* ]]> */""",
            """<!-- {"@type":"BlogPosting"} -->""",
            """{"@type":"BlogPosting"};""",
        )

        for (jsonLd in jsonLds) {
            web.enqueue(html(200, pageWithJsonLd(jsonLd)))
            val result = fetcher.fetch("http://myblog.example:${web.port}/2026/11/post")

            assertEquals(Platform.BLOG, result.platform, jsonLd)
        }
    }

    @Test
    fun `RSS·og 타입이나 안쪽 객체의 Blog 타입만 있으면 플랫폼은 OTHER다`() {
        web.enqueue(
            html(
                200,
                """
                <html><head>
                  <meta property="og:type" content="article">
                  <link rel="alternate" type="application/rss+xml" href="/rss">
                  <script type="application/ld+json">{"@type":"NewsArticle","author":{"@type":"Blog","name":"칼럼"}}</script>
                </head><body><p>기사</p></body></html>
                """.trimIndent(),
            ),
        )

        val result = fetcher.fetch("http://news.example:${web.port}/article/1")

        assertEquals(Platform.OTHER, result.platform)
    }

    @Test
    fun `노션 호스트의 페이지면 플랫폼은 NOTION이다`() {
        val paths = listOf(
            "someone.notion.site/Page-Title-0123456789abcdef0123456789abcdef",
            "notion.so/someone/Page-Title-0123456789abcdef0123456789abcdef",
            "www.notion.so/Page-Title-0123456789abcdef0123456789abcdef",
            "app.notion.com/p/Page-Title-0123456789abcdef0123456789abcdef",
        )

        for (path in paths) {
            web.enqueue(html(200, ARTICLE_HTML))
            val result = fetcher.fetch(localUrl(path))

            assertEquals(Platform.NOTION, result.platform, path)
            assertEquals(CollectLevel.AUTO, result.collectLevel, path)
        }
    }

    @Test
    fun `노션 페이지가 404면 비공개로 보고 주소의 제목 조각과 '막힘' 누락을 돌려준다`() {
        web.enqueue(MockResponse.Builder().code(404).body("Not Found").build())
        val url = "http://www.notion.so:${web.port}/someone/" +
            "%EC%97%AC%ED%96%89-%EA%B3%84%ED%9A%8D-0123456789abcdef0123456789abcdef"

        val result = fetcher.fetch(url)

        assertEquals(Platform.NOTION, result.platform)
        assertEquals(CollectLevel.AUTO, result.collectLevel)
        assertEquals(url, result.finalUrl)
        assertEquals("여행 계획", result.title)
        assertEquals(FetchMiss.BLOCKED, result.miss)
    }

    @Test
    fun `네이버 블로그 글은 모바일 주소로 받고 그 주소를 최종 주소로 돌려준다`() {
        val mobileUrl = "http://m.blog.naver.com:${web.port}/someone/223000000000"
        val urls = listOf(
            "http://blog.naver.com:${web.port}/someone/223000000000",
            "http://blog.naver.com:${web.port}/PostView.naver?blogId=someone&logNo=223000000000",
            "http://blog.naver.com:${web.port}/PostView.nhn?blogId=someone&logNo=223000000000",
            "http://blog.naver.com:${web.port}/someone?Redirect=Log&logNo=223000000000",
            "http://blog.naver.com:${web.port}/PostList.nhn?blogId=someone&logNo=223000000000&categoryNo=0",
            mobileUrl,
        )

        for (url in urls) {
            web.enqueue(html(200, ARTICLE_HTML))
            val result = fetcher.fetch(url)

            val requested = assertNotNull(web.takeRequest(1, TimeUnit.SECONDS), url).url
            assertEquals("m.blog.naver.com", requested.host, url)
            assertEquals("/someone/223000000000", requested.encodedPath, url)
            assertEquals(mobileUrl, result.finalUrl, url)
        }
    }

    @Test
    fun `네이버 블로그 글 주소가 아니면 모바일 주소로 바꾸지 않는다`() {
        val paths = listOf(
            "section.blog.naver.com/ThemePost/123",
            "blog.naver.com/someone/category",
            "blog.naver.com/Other.naver?blogId=someone&logNo=223000000000",
            "blog.naver.com/someone?logNo=223000000000",
            "blog.naver.com/PostView.naver?blogId=a/b&logNo=223000000000",
            "blog.naver.com/someone/２２３",
        )

        for (path in paths) {
            web.enqueue(html(200, ARTICLE_HTML))
            val result = fetcher.fetch(localUrl(path))

            val requested = assertNotNull(web.takeRequest(1, TimeUnit.SECONDS), path).url
            assertEquals(path.substringBefore('/'), requested.host, path)
            assertEquals(localUrl(path), result.finalUrl, path)
        }
    }

    @Test
    fun `og 태그가 없으면 title·meta description·meta author를 쓴다`() {
        web.enqueue(html(200, PLAIN_HTML))

        val result = fetcher.fetch(web.url("/post/2").toString())

        assertEquals("문서 제목", result.title)
        assertEquals("메타 설명", result.description)
        assertEquals("작성자 이름", result.author)
        assertNull(result.ogImageUrl)
    }

    @Test
    fun `본문에서 메뉴·머리말·꼬리말·스크립트·폼은 뺀다`() {
        web.enqueue(html(200, NOISY_HTML))

        val result = fetcher.fetch(web.url("/post/3").toString())

        assertEquals("본문 문단입니다.", result.body)
    }

    @Test
    fun `응답 헤더에 문자 인코딩이 없으면 페이지의 meta charset으로 읽는다`() {
        web.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/html")
                .body("""<html><head><meta charset="utf-8"><title>한글 제목</title></head><body></body></html>""")
                .build(),
        )

        val result = fetcher.fetch(web.url("/post/4").toString())

        assertEquals("한글 제목", result.title)
    }

    @Test
    fun `응답 헤더에 문자 인코딩이 있으면 그것으로 읽는다`() {
        val html = "<html><head><title>한글 제목</title></head><body></body></html>"
        web.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/html;charset=EUC-KR")
                .body(Buffer().write(html.toByteArray(Charset.forName("EUC-KR"))))
                .build(),
        )

        val result = fetcher.fetch(web.url("/post/5").toString())

        assertEquals("한글 제목", result.title)
    }

    @Test
    fun `대표 이미지가 상대 경로면 페이지 주소 기준의 절대 주소로 돌려준다`() {
        web.enqueue(html(200, """<html><head><meta property="og:image" content="/img/cover.jpg"></head></html>"""))

        val result = fetcher.fetch(web.url("/post/6").toString())

        assertEquals(web.url("/img/cover.jpg").toString(), result.ogImageUrl)
    }

    @Test
    fun `응답 헤더의 문자 인코딩을 모르면 페이지의 meta charset으로 읽는다`() {
        web.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/html; charset=utf8mb4")
                .body("""<html><head><meta charset="utf-8"><title>한글 제목</title></head><body></body></html>""")
                .build(),
        )

        val result = fetcher.fetch(web.url("/post/7").toString())

        assertEquals("한글 제목", result.title)
    }

    @Test
    fun `본문 글자가 없으면 본문은 null이다`() {
        web.enqueue(html(200, "<html><head><title>제목</title></head><body><nav>메뉴</nav></body></html>"))

        val result = fetcher.fetch(web.url("/post/8").toString())

        assertNull(result.body)
    }

    @Test
    fun `웹 페이지가 404·410이면 '없는 링크' 예외를 던진다`() {
        for (code in listOf(404, 410)) {
            web.enqueue(html(code, "<html></html>"))

            val error = assertFailsWith<AppException>("$code") { fetcher.fetch(web.url("/gone/$code").toString()) }

            assertEquals(LinkError.LINK_NOT_FOUND, error.errorCode, "$code")
        }
    }

    @Test
    fun `웹 페이지가 401·403이나 그 밖의 4xx면 '막힌 링크' 예외를 던진다`() {
        for (code in listOf(401, 403, 400, 405, 451)) {
            web.enqueue(html(code, "<html></html>"))

            val error = assertFailsWith<AppException>("$code") { fetcher.fetch(web.url("/blocked/$code").toString()) }

            assertEquals(LinkError.LINK_BLOCKED, error.errorCode, "$code")
        }
    }

    @Test
    fun `웹 페이지가 429면 '호출 한도' 예외를 던진다`() {
        web.enqueue(html(429, "<html></html>"))

        val error = assertFailsWith<AppException> { fetcher.fetch(web.url("/busy").toString()) }

        assertEquals(LinkError.LINK_RATE_LIMITED, error.errorCode)
    }

    @Test
    fun `웹 페이지가 5xx면 '받을 수 없음' 예외를 던진다`() {
        for (code in listOf(500, 502, 503)) {
            web.enqueue(html(code, "<html></html>"))

            val error = assertFailsWith<AppException>("$code") { fetcher.fetch(web.url("/down/$code").toString()) }

            assertEquals(LinkError.LINK_UNAVAILABLE, error.errorCode, "$code")
        }
    }

    @Test
    fun `웹 페이지가 제때 응답하지 않거나 연결되지 않으면 '받을 수 없음' 예외를 던진다`() {
        val quickFetcher = HttpLinkContentFetcher(
            LinkFetchProperties(
                youtubeOembedUrl = youtube.url("/oembed").toString(),
                readTimeout = Duration.ofMillis(200),
            ),
            dnsResolver = AllHostsToLoopback,
        )
        web.enqueue(html(200, ARTICLE_HTML).newBuilder().headersDelay(2, TimeUnit.SECONDS).build())
        val closedPort = ServerSocket(0).use { it.localPort }

        val slow = assertFailsWith<AppException> { quickFetcher.fetch(web.url("/slow").toString()) }
        val refused = assertFailsWith<AppException> { quickFetcher.fetch("http://closed.example:$closedPort/post") }

        assertEquals(LinkError.LINK_UNAVAILABLE, slow.errorCode)
        assertEquals(LinkError.LINK_UNAVAILABLE, refused.errorCode)
    }

    @Test
    fun `서버가 503과 Retry-After를 줘도 다시 요청하지 않는다`() {
        web.enqueue(MockResponse.Builder().code(503).addHeader("Retry-After", "1").build())
        web.enqueue(html(200, ARTICLE_HTML))

        runCatching { fetcher.fetch(web.url("/post/9").toString()) }

        assertEquals(1, web.requestCount)
    }

    @Test
    fun `앞선 응답이 준 쿠키를 다음 요청에 싣지 않는다`() {
        web.enqueue(html(200, ARTICLE_HTML).newBuilder().addHeader("Set-Cookie", "session=abc; Path=/").build())
        web.enqueue(html(200, ARTICLE_HTML))

        fetcher.fetch(web.url("/post/10").toString())
        assertNotNull(web.takeRequest(1, TimeUnit.SECONDS))

        fetcher.fetch(web.url("/post/11").toString())

        assertNull(assertNotNull(web.takeRequest(1, TimeUnit.SECONDS)).headers["Cookie"])
    }

    /** `호스트/경로` 를 가짜 서버 포트의 주소로 만든다. 호스트는 [AllHostsToLoopback] 이 가짜 서버로 보낸다. */
    private fun localUrl(hostAndPath: String): String =
        "http://${hostAndPath.substringBefore('/')}:${web.port}/${hostAndPath.substringAfter('/', "")}"

    private fun pageWithJsonLd(jsonLd: String): String =
        """<html><head><script type="application/ld+json">$jsonLd</script></head><body><p>본문</p></body></html>"""

    private fun json(code: Int, body: String): MockResponse = MockResponse.Builder()
        .code(code)
        .addHeader("Content-Type", "application/json;charset=UTF-8")
        .body(body)
        .build()

    private fun html(code: Int, body: String): MockResponse = MockResponse.Builder()
        .code(code)
        .addHeader("Content-Type", "text/html;charset=UTF-8")
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

        private val ARTICLE_HTML = """
            <html>
              <head>
                <meta property="og:title" content="글 제목">
                <meta property="og:description" content="글 설명">
                <meta property="og:image" content="https://img.example.com/cover.jpg">
              </head>
              <body>
                <p>첫 문단입니다.</p>
                <p>둘째 문단입니다.</p>
              </body>
            </html>
        """.trimIndent()

        private val PLAIN_HTML = """
            <html>
              <head>
                <title>문서 제목</title>
                <meta name="description" content="메타 설명">
                <meta name="author" content="작성자 이름">
              </head>
              <body><p>본문</p></body>
            </html>
        """.trimIndent()

        private val NOISY_HTML = """
            <html>
              <head><title>제목</title><style>p { color: red; }</style></head>
              <body>
                <header>사이트 이름</header>
                <nav>홈 메뉴</nav>
                <p>본문 문단입니다.</p>
                <script>console.log("스크립트")</script>
                <noscript>스크립트를 켜세요</noscript>
                <svg><text>그림 글자</text></svg>
                <form><label>댓글 입력</label></form>
                <footer>저작권 안내</footer>
              </body>
            </html>
        """.trimIndent()
    }

    /** 모든 호스트를 가짜 서버가 있는 127.0.0.1 로 보낸다. 실제 사이트로는 요청이 나가지 않는다. */
    private object AllHostsToLoopback : DnsResolver {
        override fun resolve(host: String): Array<InetAddress> = arrayOf(InetAddress.getLoopbackAddress())

        override fun resolveCanonicalHostname(host: String): String = host
    }
}
