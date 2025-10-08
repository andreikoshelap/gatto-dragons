package com.gatto.dragon.api;

import com.gatto.dragon.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameClient {
    private final RestClient http;

    public GameStart start() {
        return http.post()
                .uri("/api/v2/game/start")
                .retrieve()
                .body(GameStart.class);
    }

    public List<ShopItem> shop(String gameId) {
        try {
            var arr = http.get()
                    .uri("/api/v2/{id}/shop", gameId)
                    .retrieve()
                    .body(ShopItem[].class);
            return arr == null ? List.of() : List.of(arr);
        } catch (HttpClientErrorException e) {
            // 410 -> Game Over
            if (e.getStatusCode() == HttpStatus.GONE) {
                log.info("Game Over (410) on /shop for gameId={}: {}", gameId, e.getResponseBodyAsString());
                return null;
            }
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.debug("404 on /shop for gameId={}: {}", gameId, e.getResponseBodyAsString());
                return null;
            }
            throw e;
        } catch (RestClientResponseException e) {
            log.warn("HTTP {} on /shop for gameId={}, body={}",
                    e.getStatusCode(), gameId, e.getResponseBodyAsString());
            throw e;
        }
    }

    public SolveResult solve(String gameId, String adId) {
        try {
            var uri = UriComponentsBuilder.fromPath("/api/v2/{id}/solve/{ad}")
                    .buildAndExpand(gameId, adId == null ? "" : adId.trim())
                    .encode()
                    .toUri();

            return http.post().uri(uri).retrieve().body(SolveResult.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.GONE) {
                log.info("Game Over (410) on /solve for gameId={}: {}", gameId, e.getResponseBodyAsString());
                return null;
            }
            throw e;
        }
    }

    public List<Message> messages(String gameId) {
        try {
            var arr = http.get()
                    .uri("/api/v2/{id}/messages", gameId)
                    .retrieve()
                    .body(Message[].class);
            return arr == null ? List.of() : List.of(arr);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.GONE || e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.info("Game ended ({}): /messages gameId={}, body={}",
                        e.getStatusCode(), gameId, e.getResponseBodyAsString());
                return null;
            }
            throw e;
        }
    }

    public PurchaseResult purchase(String gameId, String itemIdRaw) {
        try {
            var uri = UriComponentsBuilder.fromPath("/api/v2/{id}/shop/buy/{item}")
                    .buildAndExpand(gameId, itemIdRaw == null ? "" : itemIdRaw.trim())
                    .encode()
                    .toUri();
            return http.post().uri(uri).retrieve().body(PurchaseResult.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.GONE) {
                log.info("Game Over (410) on /shop/buy for gameId={}: {}", gameId, e.getResponseBodyAsString());
                return null;
            }
            throw e;
        }
    }
}
