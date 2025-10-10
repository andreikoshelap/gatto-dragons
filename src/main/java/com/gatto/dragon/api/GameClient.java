package com.gatto.dragon.api;

import com.gatto.dragon.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GameClient {
    private final WebClient http;

    public Mono<Game> start() {
        return http.post()
                .uri("/api/v2/game/start")
                .retrieve()
                .bodyToMono(Game.class);
    }

    public Mono<List<Message>> messages(String gameId) {
        return http.get()
                .uri("/api/v2/{id}/messages", gameId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, r -> r.bodyToMono(String.class)
                        .flatMap(body -> Mono.error(new RuntimeException("4xx: " + body))))
                .onStatus(HttpStatusCode::is5xxServerError, r -> r.bodyToMono(String.class)
                        .flatMap(body -> Mono.error(new RuntimeException("5xx: " + body))))
                .bodyToFlux(Message.class)
                .collectList()
                .timeout(Duration.ofSeconds(5))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(200))
                        .filter(ex -> ex instanceof IOException));
    }

    public Mono<SolveResult> solve(String gameId, String adId) {
        return http.post()
                .uri("/api/v2/{id}/solve/{ad}", gameId, adId)
                .retrieve()
                .onStatus(s -> s.value()==410, r -> Mono.empty()) // : Game Over
                .onStatus(HttpStatusCode::is4xxClientError, r -> r.bodyToMono(String.class)
                        .flatMap(body -> Mono.error(new RuntimeException("4xx: " + body))))
                .onStatus(HttpStatusCode::is5xxServerError, r -> r.bodyToMono(String.class)
                        .flatMap(body -> Mono.error(new RuntimeException("5xx: " + body))))
                .bodyToMono(SolveResult.class)
                .timeout(Duration.ofSeconds(5))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(200))
                        .filter(ex -> ex instanceof IOException));
    }

    public Mono<List<ShopItem>> shop(String gameId) {
        return http.get()
                .uri("/api/v2/{id}/shop", gameId)
                .retrieve()
                .bodyToFlux(ShopItem.class)
                .collectList();
    }

    public Mono<PurchaseResult> purchase(String gameId, String itemId) {
        return http.post()
                .uri("/api/v2/{id}/shop/buy/{item}", gameId, itemId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, r -> r.bodyToMono(String.class)
                        .flatMap(body -> Mono.error(new RuntimeException("4xx: " + body))))
                .onStatus(HttpStatusCode::is5xxServerError, r -> r.bodyToMono(String.class)
                        .flatMap(body -> Mono.error(new RuntimeException("5xx: " + body))))
                .bodyToMono(PurchaseResult.class)
                .timeout(Duration.ofSeconds(5))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(200))
                        .filter(ex -> ex instanceof IOException));
    }

    public Mono<Reputation> investigateReputation(String gameId) {
        var uri = UriComponentsBuilder.fromPath("/api/v2/{id}/investigate/reputation")
                .buildAndExpand(gameId)
                .encode()
                .toUri();
            return http.post()
                    .uri(uri)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, r -> r.bodyToMono(String.class)
                            .flatMap(body -> Mono.error(new RuntimeException("4xx: " + body))))
                    .onStatus(HttpStatusCode::is5xxServerError, r -> r.bodyToMono(String.class)
                            .flatMap(body -> Mono.error(new RuntimeException("5xx: " + body))))
                    .bodyToMono(Reputation.class)
                    .timeout(Duration.ofSeconds(5))
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(200))
                            .filter(ex -> ex instanceof IOException));
    }
}
