package study.min.order.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

/**
 * JPA Auditing 활성화
 * - @CreatedDate, @LastModifiedDate 자동 처리
 */
@Configuration
@EnableJpaAuditing
class JpaConfig
