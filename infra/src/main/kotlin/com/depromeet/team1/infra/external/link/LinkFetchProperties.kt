package com.depromeet.team1.infra.external.link

import java.time.Duration

/** 링크 수집 설정. 필요한 키는 없다. */
data class LinkFetchProperties(
    val youtubeOembedUrl: String = "https://www.youtube.com/oembed",
    val connectTimeout: Duration = Duration.ofSeconds(3),
    val readTimeout: Duration = Duration.ofSeconds(10),
)
