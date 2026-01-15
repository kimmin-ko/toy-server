package study.min.order.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

/**
 * RestClient Configuration
 * - Spring 6.1+ RestClient 사용 (동기식 HTTP 클라이언트)
 * - Product Service REST API 호출용
 */
@Configuration
class RestClientConfig {

    @Bean
    fun productRestClient(
        @Value("\${rest.client.product-service.url}") baseUrl: String
    ): RestClient {
        return RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultStatusHandler(HttpStatusCode::is4xxClientError) { _, response ->
                throw RuntimeException("Client error: ${response.statusCode}")
            }
            .defaultStatusHandler(HttpStatusCode::is5xxServerError) { _, response ->
                throw RuntimeException("Server error: ${response.statusCode}")
            }
            .build()
    }
}
