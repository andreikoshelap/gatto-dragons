package com.gatto.dragon.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Bean
    WebClient gameWebClient() {
        var httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)          // connect timeout
                .responseTimeout(Duration.ofSeconds(6))                       // server-side read timeout
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(6, TimeUnit.SECONDS))   // socket read
                        .addHandlerLast(new WriteTimeoutHandler(6, TimeUnit.SECONDS))); // socket write

        return WebClient.builder()
                .baseUrl("https://dragonsofmugloar.com")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(ExchangeFilterFunction.ofResponseProcessor(Mono::just))
                .build();
    }
}
