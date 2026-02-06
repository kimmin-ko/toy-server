package study.min.order.config

import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class BigQueryConfig {

    @Value("\${bigquery.project-id:test-project}")
    private lateinit var projectId: String

    @Value("\${bigquery.emulator.host:localhost}")
    private lateinit var emulatorHost: String

    @Value("\${bigquery.emulator.port:9050}")
    private var emulatorPort: Int = 9050

    @Bean
    fun bigQuery(): BigQuery {
        return BigQueryOptions.newBuilder()
            .setProjectId(projectId)
            .setHost("http://$emulatorHost:$emulatorPort")
            .build()
            .service
    }
}
