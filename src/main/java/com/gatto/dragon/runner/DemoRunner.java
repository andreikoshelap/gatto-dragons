package com.gatto.dragon.runner;

import com.gatto.dragon.dto.Game;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class DemoRunner implements CommandLineRunner {
    private final GameRunner runner;

    @Override
    public void run(String... args) {
        int N = 5;
        int concurrency = 5;

        Flux.range(0, N)
                .flatMap(i ->
                                runner.playOne()
                                        .doOnSubscribe(s -> log.info("Start game #{}", i))
                                        .doOnNext(g -> log.info("End   game #{} id={} score={}", i, g.gameId(), g.score())),
                        concurrency
                )
                .map(Game::score)
                .collectList()
                .doOnNext(scores -> {
                    int max = scores.stream().mapToInt(x -> x).max().orElse(0);
                    log.info("Games={} | scores={} |  max={}", scores.size(), scores, max);
                })
                .block();
    }
}
