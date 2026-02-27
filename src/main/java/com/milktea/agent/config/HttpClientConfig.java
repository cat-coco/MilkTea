package com.milktea.agent.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

/**
 * Configures Apache HttpClient 5 as the underlying HTTP transport for Spring's RestClient.
 *
 * <p>Root cause of the "HTTP/1.1 header parser received no bytes" error:
 * The default JDK HttpClient reuses pooled keep-alive connections without checking
 * whether the server has already closed them. When a stale connection is used the
 * server sends no response bytes, which causes the HTTP/1.1 header parser to fail.
 *
 * <p>Fix: Apache HttpClient 5 detects stale connections before reuse
 * ({@code setValidateAfterInactivity}) and proactively evicts expired / idle connections
 * from the pool, so the retry template in Spring AI always gets a live connection.
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public RestClientCustomizer apacheHttpClientCustomizer() {
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(
                        PoolingHttpClientConnectionManagerBuilder.create()
                                .setDefaultConnectionConfig(
                                        ConnectionConfig.custom()
                                                // Validate pooled connections after 1 s of inactivity
                                                // before reusing them — this is the primary fix.
                                                .setValidateAfterInactivity(TimeValue.ofSeconds(1))
                                                // Hard upper bound on connection lifetime in the pool.
                                                .setTimeToLive(TimeValue.ofSeconds(120))
                                                .setConnectTimeout(Timeout.ofSeconds(10))
                                                .setSocketTimeout(Timeout.ofSeconds(60))
                                                .build()
                                )
                                .setMaxConnTotal(50)
                                .setMaxConnPerRoute(20)
                                .build()
                )
                // Background thread evicts connections that have passed their TTL.
                .evictExpiredConnections()
                // Also evict connections that have been idle for more than 30 s.
                .evictIdleConnections(TimeValue.ofSeconds(30))
                .build();

        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        // RestClientCustomizer is applied to the auto-configured RestClient.Builder
        // that Spring AI's autoconfiguration injects, so every AI HTTP call uses
        // this properly managed connection pool.
        return builder -> builder.requestFactory(factory);
    }
}
