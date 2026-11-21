package com.depromeet.team1.infra.external.queue

import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.http.apache5.Apache5HttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import java.net.URI
import java.time.Duration

/** SQS 클라이언트를 만든다. */
object SqsClients {
    private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)

    /** 롱 폴링(최대 20초) 동안 응답을 기다려야 해서 그보다 길게 둔다. */
    private val READ_TIMEOUT: Duration = SqsJobQueue.LONG_POLLING.plusSeconds(10)

    /**
     * [endpoint] 가 있으면 그 주소의 SQS 호환 서버(로컬·테스트의 ElasticMQ)에 자격 증명 없이 붙는다.
     * 없으면 AWS SQS 에 붙고, 자격 증명은 AWS SDK 기본 순서(환경변수, 인스턴스 역할 등)로 찾는다.
     */
    fun create(region: String, endpoint: URI?): SqsClient {
        val builder = SqsClient.builder()
            .region(Region.of(region))
            .httpClientBuilder(
                Apache5HttpClient.builder().connectionTimeout(CONNECT_TIMEOUT).socketTimeout(READ_TIMEOUT),
            )
            .overrideConfiguration { it.apiCallAttemptTimeout(READ_TIMEOUT) }
        if (endpoint != null) {
            builder.endpointOverride(endpoint).credentialsProvider(AnonymousCredentialsProvider.create())
        }
        return builder.build()
    }
}
