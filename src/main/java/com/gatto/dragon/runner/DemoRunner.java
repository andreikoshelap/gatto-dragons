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
        int N = 2;
        int concurrency = Math.min(4, N);              // настроить при желании

        Flux.range(0, N)
                .flatMap(i -> runner.playOne(), concurrency) // параллельный запуск до 'concurrency'
                .map(Game::score)
                .collectList()
                .doOnNext(scores ->
                        System.out.printf("Games=%d | scores=%s%n", N, scores)
                )
                .block();
    }

}
