package com.depromeet.team1.infra.external.link

import com.depromeet.team1.core.item.CollectLevel
import com.depromeet.team1.core.item.FetchedLink
import com.depromeet.team1.core.item.LinkContentFetcher
import com.depromeet.team1.core.item.Platform
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import java.net.http.HttpClient

/**
 * 링크 수집기. 유튜브는 oEmbed(유튜브 공식 미리보기 API)로 받는다. oEmbed 응답 DTO 는 이 파일 밖으로 내보내지 않는다.
 */
class HttpLinkContentFetcher(private val properties: LinkFetchProperties) : LinkContentFetcher {
    private val restClient: RestClient = RestClient.builder()
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.connectTimeout).build(),
            ).apply { setReadTimeout(properties.readTimeout) },
        )
        .build()

    override fun fetch(url: String): FetchedLink {
        val videoId = checkNotNull(youtubeVideoId(url)) { "유튜브 영상 주소가 아니다" }
        val oembed = restClient.get()
            .uri("${properties.youtubeOembedUrl}?url={url}&format=json", "https://www.youtube.com/watch?v=$videoId")
            .retrieve()
            .body(YouTubeOembed::class.java)
        return FetchedLink(
            platform = Platform.YOUTUBE,
            collectLevel = CollectLevel.AUTO,
            title = oembed?.title,
            author = oembed?.authorName,
            ogImageUrl = oembed?.thumbnailUrl,
        )
    }
}

/** 유튜브 주소에서 영상 ID 를 뽑는다. watch·youtu.be·shorts·live·embed 형태를 받는다. */
private fun youtubeVideoId(url: String): String? {
    val uri = UriComponentsBuilder.fromUriString(url).build()
    val segments = uri.pathSegments
    return when (uri.host) {
        "youtu.be" -> segments.firstOrNull()
        "www.youtube.com", "m.youtube.com" -> when (segments.firstOrNull()) {
            "watch" -> uri.queryParams.getFirst("v")
            "shorts", "live", "embed" -> segments.getOrNull(1)
            else -> null
        }
        else -> null
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class YouTubeOembed(
    val title: String?,
    @JsonProperty("author_name") val authorName: String?,
    @JsonProperty("thumbnail_url") val thumbnailUrl: String?,
)
