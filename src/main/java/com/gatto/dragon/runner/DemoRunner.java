package com.gatto.dragon.runner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class DemoRunner implements org.springframework.boot.CommandLineRunner {

    private final GameRunner runner;

    @Override
    public void run(String... args) throws Exception {
        int N = 16;

        try (var exec = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory())) {
            List<Future<Integer>> futures = IntStream.range(0, N)
                    .mapToObj(i -> exec.submit(() -> runner.playOne().score()))
                    .toList();

            var scores = futures.stream().map(f -> {
                try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); }
            }).toList();

            System.out.printf("Games=%d | scores=%s%n", N, scores);
        }
    }
}
