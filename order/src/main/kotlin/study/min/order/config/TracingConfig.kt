package study.min.order.config

import brave.Tracing
import brave.context.slf4j.MDCScopeDecorator
import brave.propagation.ThreadLocalCurrentTraceContext
import brave.sampler.Sampler
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Configuration
class TracingConfig {

    @Bean
    fun tracing(): Tracing {
        val currentTraceContext = ThreadLocalCurrentTraceContext.newBuilder()
            .addScopeDecorator(MDCScopeDecorator.get())
            .build()

        return Tracing.newBuilder()
            .currentTraceContext(currentTraceContext)
            .sampler(Sampler.ALWAYS_SAMPLE)
            .localServiceName("order")
            .build()
    }
}

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class TracingFilter(private val tracing: Tracing) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val span = tracing.tracer()
            .newTrace()
            .name("${request.method} ${request.requestURI}")
            .start()

        tracing.currentTraceContext().newScope(span.context()).use {
            try {
                filterChain.doFilter(request, response)
            } finally {
                span.finish()
            }
        }
    }
}
