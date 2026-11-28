package com.depromeet.team1.infra.external.link

import com.depromeet.team1.core.common.error.AppException
import com.depromeet.team1.core.item.CollectLevel
import com.depromeet.team1.core.item.FetchMiss
import com.depromeet.team1.core.item.FetchedLink
import com.depromeet.team1.core.item.LinkContentFetcher
import com.depromeet.team1.core.item.LinkError
import com.depromeet.team1.core.item.Platform
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.hc.client5.http.DnsResolver
import org.apache.hc.client5.http.SystemDefaultDnsResolver
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.core5.util.Timeout
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.InvalidMediaTypeException
import org.springframework.http.MediaType
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.util.UriComponents
import org.springframework.web.util.UriComponentsBuilder
import org.springframework.web.util.UriUtils
import tools.jackson.core.JacksonException
import tools.jackson.core.json.JsonReadFeature
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient

/**
 * 링크 수집기. 유튜브는 oEmbed(유튜브 공식 미리보기 API)로, 그 외는 웹 페이지 HTML 로 받는다. oEmbed 응답 DTO 는 이 파일 밖으로 내보내지 않는다.
 *
 * 웹 페이지는 [dnsResolver] 가 정한 IP 로 연결한다. 테스트는 모든 호스트를 가짜 서버로 보내는 해석기를 넣는다.
 */
class HttpLinkContentFetcher(
    private val properties: LinkFetchProperties,
    dnsResolver: DnsResolver = SystemDefaultDnsResolver.INSTANCE,
) : LinkContentFetcher {
    private val oembedClient: RestClient = RestClient.builder()
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.connectTimeout).build(),
            ).apply { setReadTimeout(properties.readTimeout) },
        )
        .build()

    private val webClient: RestClient = RestClient.builder()
        .requestFactory(
            HttpComponentsClientHttpRequestFactory(
                HttpClients.custom()
                    .setConnectionManager(
                        PoolingHttpClientConnectionManagerBuilder.create()
                            .setDnsResolver(dnsResolver)
                            .setDefaultConnectionConfig(
                                ConnectionConfig.custom()
                                    .setConnectTimeout(Timeout.of(properties.connectTimeout))
                                    .setSocketTimeout(Timeout.of(properties.readTimeout))
                                    .build(),
                            )
                            .build(),
                    )
                    .disableRedirectHandling()
                    // 기본 재시도는 Retry-After 만큼 상한 없이 기다린다. 다시 할지는 작업 재시도가 정한다.
                    .disableAutomaticRetries()
                    // 링크마다 따로 받는 요청이라 쿠키를 남기지 않는다. 남기면 다른 회원의 요청에 실린다.
                    .disableCookieManagement()
                    .build(),
            ),
        )
        .build()

    override fun fetch(url: String): FetchedLink {
        val uri = UriComponentsBuilder.fromUriString(url).build()
        // 호스트는 대소문자를 가리지 않고, 끝의 점(`musinsa.com.`)은 같은 호스트다.
        val host = uri.host?.lowercase()?.removeSuffix(".").orEmpty()
        if (host.isUnder(NOT_REQUESTED_DOMAINS)) return notRequested(url, Platform.OTHER)
        if (host == YOUTUBE_SHORT_HOST || host.isUnder(YOUTUBE_DOMAINS)) {
            // 유튜브는 oEmbed 만 쓴다. 영상이 아닌 주소(채널, 재생목록 등)는 페이지를 요청하지 않는다.
            val videoId = youtubeVideoId(uri, host) ?: return notRequested(url, Platform.YOUTUBE)
            return fetchYouTubeVideo(url, videoId)
        }
        val platform = when {
            host.isUnder(BLOG_DOMAINS) -> Platform.BLOG
            host.isUnder(NOTION_DOMAINS) -> Platform.NOTION
            else -> Platform.OTHER
        }
        return fetchWebPage(naverBlogMobileUrl(uri, host) ?: url, platform)
    }

    private fun fetchYouTubeVideo(url: String, videoId: String): FetchedLink {
        val oembed = call {
            oembedClient.get()
                .uri("${properties.youtubeOembedUrl}?url={url}&format=json", "https://www.youtube.com/watch?v=$videoId")
                .retrieve()
                .body(YouTubeOembed::class.java)
        }
        return FetchedLink(
            finalUrl = url,
            platform = Platform.YOUTUBE,
            collectLevel = CollectLevel.AUTO,
            title = oembed?.title,
            author = oembed?.authorName,
            description = null,
            body = null,
            ogImageUrl = oembed?.thumbnailUrl,
            miss = null,
        )
    }

    /**
     * 요청의 외부 오류를 내부 예외로 바꾼다. 호출 한도 초과(429)는 다른 오류와 구분한다.
     * 원인 예외는 붙이지 않는다. 원인의 메시지에 요청 주소가 들어 있어 에러 로그에 그대로 찍히기 때문이다.
     */
    private fun <T> call(block: () -> T): T = try {
        block()
    } catch (e: RestClientResponseException) {
        throw AppException(e.statusCode.toLinkError() ?: LinkError.LINK_UNAVAILABLE, "status=${e.statusCode.value()}")
    } catch (e: RestClientException) {
        throw AppException(LinkError.LINK_UNAVAILABLE, e.javaClass.simpleName)
    }

    private fun fetchWebPage(url: String, platform: Platform): FetchedLink {
        // 응답 변환기를 거치지 않고 바이트로 받는다. 변환기는 Content-Type 의 모르는 charset 에서 예외를 던진다.
        val page = call {
            webClient.get().uri(URI.create(url)).exchange { _, response ->
                when {
                    // 노션은 봇 UA 로 요청한 비공개 페이지에 404 를 준다. 없는 페이지와 구분되지 않아 둘 다 비공개로 본다.
                    platform == Platform.NOTION && response.statusCode.value() == 404 -> null
                    else -> response.statusCode.toLinkError()?.let {
                        throw AppException(it, "status=${response.statusCode.value()}")
                    }
                        ?: RawPage(response.headers.getFirst(HttpHeaders.CONTENT_TYPE), response.body.readAllBytes())
                }
            }
        } ?: return privateNotion(url)
        val document = Jsoup.parse(ByteArrayInputStream(page.body), page.charset(), url)
        return FetchedLink(
            finalUrl = url,
            // 목록에 없는 블로그도 페이지가 블로그 글이라고 밝히면 BLOG 다.
            platform = if (platform == Platform.OTHER && document.declaresBlog()) Platform.BLOG else platform,
            collectLevel = CollectLevel.AUTO,
            title = document.meta("property", "og:title") ?: document.title().ifBlank { null },
            author = document.meta("name", "author"),
            description = document.meta("property", "og:description") ?: document.meta("name", "description"),
            body = document.bodyText(),
            ogImageUrl = document.selectFirst("meta[property=og:image]")?.absUrl("content")?.ifBlank { null },
            miss = null,
        )
    }

    private companion object {
        /** 요청하지 않는 곳. 약관이나 봇 차단 때문에 요청하지 않는다. 도메인과 그 하위 도메인을 모두 막는다. */
        val NOT_REQUESTED_DOMAINS = setOf(
            // SNS
            "instagram.com",
            "x.com",
            "twitter.com",
            "pinterest.com",
            "pinterest.co.kr",
            // 지도
            "map.kakao.com",
            "map.naver.com",
            // 쇼핑·선물
            "gift.kakao.com",
            "coupang.com",
            "smartstore.naver.com",
            "musinsa.com",
            "29cm.co.kr",
            "ohou.se",
            // 뉴스
            "news.naver.com",
        )

        /** 블로그. 도메인과 그 하위 도메인이 BLOG 다. 목록에 없어도 페이지가 JSON-LD 로 블로그라고 밝히면 BLOG 다. */
        val BLOG_DOMAINS = setOf(NAVER_BLOG_HOST, "tistory.com", "velog.io", "brunch.co.kr", "medium.com")

        /** 노션. 공개 페이지(`*.notion.site`)와 노션 앱 주소다. */
        val NOTION_DOMAINS = setOf("notion.site", "notion.so", "app.notion.com")

        /** 유튜브. 도메인과 그 하위 도메인, 그리고 단축 주소 호스트(`youtu.be`)가 YOUTUBE 다. */
        val YOUTUBE_DOMAINS = setOf("youtube.com", "youtube-nocookie.com")
    }
}

/** 없는 글로 보는 응답 상태. */
private val NOT_FOUND_STATUSES = setOf(404, 410)

/** 오류 응답 상태를 링크 오류로 바꾼다. 오류가 아니면 null 이다. 4xx 는 다시 해도 소용없고, 429 와 5xx 는 다시 하면 될 수 있다. */
private fun HttpStatusCode.toLinkError(): LinkError? = when {
    value() in NOT_FOUND_STATUSES -> LinkError.LINK_NOT_FOUND
    value() == HttpStatus.TOO_MANY_REQUESTS.value() -> LinkError.LINK_RATE_LIMITED
    is4xxClientError -> LinkError.LINK_BLOCKED
    isError -> LinkError.LINK_UNAVAILABLE
    else -> null
}

/** 호스트가 [domains] 의 도메인이거나 그 하위 도메인인지. */
private fun String.isUnder(domains: Set<String>): Boolean = domains.any { this == it || endsWith(".$it") }

/** 비공개 노션 페이지의 결과. 주소의 제목 조각만 제목으로 쓴다. */
private fun privateNotion(url: String) = FetchedLink(
    finalUrl = url,
    platform = Platform.NOTION,
    collectLevel = CollectLevel.AUTO,
    title = notionTitleFragment(url),
    author = null,
    description = null,
    body = null,
    ogImageUrl = null,
    miss = FetchMiss.BLOCKED,
)

/** 노션 주소 끝의 `{제목}-{페이지 ID 32자}` 에서 제목 조각. 하이픈은 띄어쓰기로 바꾼다. 조각이 없으면 null 이다. */
private fun notionTitleFragment(url: String): String? {
    val lastSegment = UriComponentsBuilder.fromUriString(url).build().pathSegments.lastOrNull() ?: return null
    return UriUtils.decode(lastSegment, Charsets.UTF_8)
        .replace(NOTION_PAGE_ID_SUFFIX, "")
        .replace('-', ' ')
        .trim()
        .ifBlank { null }
}

private val NOTION_PAGE_ID_SUFFIX = Regex("-?[0-9a-f]{32}$")

/** 요청하지 않은 링크의 결과. 수집 수준은 경로로 정하므로 LINK_MEMO 다. */
private fun notRequested(url: String, platform: Platform) = FetchedLink(
    finalUrl = url,
    platform = platform,
    collectLevel = CollectLevel.LINK_MEMO,
    title = null,
    author = null,
    description = null,
    body = null,
    ogImageUrl = null,
    miss = FetchMiss.NOT_REQUESTED,
)

/** 변환하지 않은 웹 페이지 응답. */
private class RawPage(private val contentType: String?, val body: ByteArray) {
    /** 헤더의 문자 인코딩. 없거나 모르는 값이면 null 로 두어 jsoup 이 페이지의 meta charset 으로 정하게 한다. */
    fun charset(): String? = try {
        contentType?.let { MediaType.parseMediaType(it).charset?.name() }
    } catch (_: InvalidMediaTypeException) {
        null
    }
}

/** 본문 글자. 본문이 아닌 영역(#47 방식)을 빼고 남은 글자를 공백 하나로 이어 붙인다. */
private fun Document.bodyText(): String? {
    val body = body().clone()
    body.select(NON_CONTENT_TAGS).remove()
    return body.text().ifBlank { null }
}

private const val NON_CONTENT_TAGS = "script, style, noscript, svg, nav, footer, header, form"

/**
 * 페이지가 JSON-LD 최상위 타입(`@graph` 항목 포함)으로 블로그 글이나 블로그(`BlogPosting`, `Blog`)라고 밝혔는지.
 * 안쪽 객체(작성자 등)의 타입은 보지 않는다. RSS·generator·og:type 은 뉴스에도 있어 쓰지 않는다.
 */
private fun Document.declaresBlog(): Boolean = select("script[type=application/ld+json]").any { script ->
    jsonLdTopLevelTypes(script.data()).any { it in BLOG_JSON_LD_TYPES }
}

/** JSON-LD 최상위 항목들의 `@type` 이름. `http://schema.org/BlogPosting` 같은 전체 주소는 끝의 이름만 쓴다. 읽지 못하면 비어 있다. */
private fun jsonLdTopLevelTypes(json: String): List<String> {
    val root = try {
        LENIENT_JSON.readTree(json.withoutHtmlWrapper())
    } catch (_: JacksonException) {
        return emptyList()
    }
    val items = root.asNodes()
    return (items + items.flatMap { it.path("@graph").asNodes() })
        .flatMap { it.path("@type").asNodes() }
        .filter { it.isString }
        .map { it.stringValue().substringAfterLast('/').substringAfterLast(':') }
}

/** 배열이면 그 항목들, 값이 없으면 빈 목록, 그 밖에는 자기 하나다. JSON-LD 는 항목이 하나면 배열 없이 쓸 수 있다. */
private fun JsonNode.asNodes(): List<JsonNode> = when {
    isArray -> toList()
    isMissingNode || isNull -> emptyList()
    else -> listOf(this)
}

/** 앞뒤의 HTML 주석 표시(`<!--`, `-->`)를 떼고, CDATA 표시(`<![CDATA[`, `]]>`)는 어디에 있든 지운다. 그 표시를 감싼 `/* */` 는 주석 허용으로 읽는다. */
private fun String.withoutHtmlWrapper(): String =
    trim().removePrefix("<!--").removeSuffix("-->").replace("<![CDATA[", "").replace("]]>", "")

/** 실제 페이지의 JSON-LD 는 엄격한 JSON 이 아닐 때가 많다. 문자열 안 줄바꿈, 끝의 쉼표, 주석을 받아 주고, 첫 값 뒤에 남은 글자(`;` 등)는 읽지 않는다. */
private val LENIENT_JSON: JsonMapper = JsonMapper.builder()
    .enable(
        JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS,
        JsonReadFeature.ALLOW_TRAILING_COMMA,
        JsonReadFeature.ALLOW_JAVA_COMMENTS,
    )
    .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
    .build()

private val BLOG_JSON_LD_TYPES = setOf("BlogPosting", "Blog")

/** `<meta {attribute}="{value}" content="…">` 의 content. 없거나 비어 있으면 null 이다. */
private fun Document.meta(attribute: String, value: String): String? =
    selectFirst("meta[$attribute=$value]")?.attr("content")?.ifBlank { null }

/**
 * 네이버 블로그 글 주소를 모바일 주소(`m.blog.naver.com/{blogId}/{logNo}`)로 바꾼다.
 * PC 주소의 HTML 은 본문을 iframe 안에 둬서 모바일 주소로 받는다. 글 주소가 아니면 null 이다.
 */
private fun naverBlogMobileUrl(uri: UriComponents, host: String): String? {
    if (host != NAVER_BLOG_HOST && host != NAVER_BLOG_MOBILE_HOST) return null
    val segments = uri.pathSegments
    val query = uri.queryParams
    val first = segments.firstOrNull()
    val (blogId, logNo) = when {
        // 글 보기·글 목록 화면(`PostView.naver?blogId=&logNo=`). 다른 `.naver`·`.nhn` 화면은 글 주소가 아니다.
        first != null && first.isNaverPage() ->
            if (first.substringBeforeLast('.') in
                NAVER_BLOG_POST_PAGES
            ) {
                query.getFirst("blogId") to query.getFirst("logNo")
            } else {
                return null
            }
        // 옛 공유 주소 `/{blogId}?Redirect=Log&logNo={logNo}`
        segments.size == 1 && query.getFirst("Redirect") == "Log" -> segments[0] to query.getFirst("logNo")
        segments.size == 2 -> segments[0] to segments[1]
        else -> return null
    }
    if (blogId == null ||
        !NAVER_BLOG_ID.matches(blogId) ||
        logNo.isNullOrEmpty() ||
        !logNo.all { it in '0'..'9' }
    ) {
        return null
    }
    return UriComponentsBuilder.newInstance()
        .scheme(uri.scheme)
        .host(NAVER_BLOG_MOBILE_HOST)
        .port(uri.port)
        .pathSegment(blogId, logNo)
        .build()
        .toUriString()
}

/** 유튜브 주소에서 영상 ID 를 뽑는다. watch·youtu.be·shorts·live·embed·v 형태를 받고, 영상이 아니면 null 이다. */
private fun youtubeVideoId(uri: UriComponents, host: String): String? {
    val segments = uri.pathSegments
    val candidate = if (host == YOUTUBE_SHORT_HOST) {
        segments.firstOrNull()
    } else {
        when (segments.firstOrNull()) {
            "watch" -> uri.queryParams.getFirst("v")
            // 옛 임베드 주소는 `?` 없이 `/v/{id}&hl=en` 처럼 경로 뒤에 `&` 로 값을 붙인다. youtu.be 는 이 형태가 없어 자르지 않는다.
            "shorts", "live", "embed", "v" -> segments.getOrNull(1)?.substringBefore('&')
            else -> null
        }
    }
    return candidate?.takeIf { YOUTUBE_VIDEO_ID.matches(it) && it !in YOUTUBE_NON_VIDEO_EMBEDS }
}

/** 영상 ID 자리에 오지만 영상이 아닌 임베드 경로. 재생목록(`videoseries`)과 채널 라이브(`live_stream`)다. 둘 다 11자다. */
private val YOUTUBE_NON_VIDEO_EMBEDS = setOf("videoseries", "live_stream")

/** 유튜브 영상 ID 는 영문·숫자·`_`·`-` 11자다. */
private val YOUTUBE_VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")

private const val YOUTUBE_SHORT_HOST = "youtu.be"

private const val NAVER_BLOG_HOST = "blog.naver.com"

private const val NAVER_BLOG_MOBILE_HOST = "m.blog.naver.com"

/** 쿼리의 `logNo` 로 글 하나를 가리키는 네이버 블로그 화면. 확장자(`.naver`, 옛 공유 링크의 `.nhn`)는 뗀 이름이다. */
private val NAVER_BLOG_POST_PAGES = setOf("PostView", "PostList")

/** 네이버 블로그 아이디. 쿼리에서 온 값은 `/`·`?` 가 섞일 수 있어 이 형태일 때만 주소에 넣는다. */
private val NAVER_BLOG_ID = Regex("[A-Za-z0-9_-]+")

/** 네이버 화면 경로(`PostView.naver`, `PostList.nhn` 등)인지. */
private fun String.isNaverPage(): Boolean = endsWith(".naver") || endsWith(".nhn")

@JsonIgnoreProperties(ignoreUnknown = true)
private data class YouTubeOembed(
    val title: String?,
    @JsonProperty("author_name") val authorName: String?,
    @JsonProperty("thumbnail_url") val thumbnailUrl: String?,
)
