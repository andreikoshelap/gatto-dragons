package com.gatto.dragon.runner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class DemoRunner implements CommandLineRunner {
    private final GameRunner runner;

    @Override public void run(String... args) {
        int N = 2;
        var scores = new ArrayList<Integer>();
        for (int i = 0; i < N; i++) {
            var end = runner.playOne();
            scores.add(end.score());
        }
        int avg = (int)Math.round(scores.stream().mapToInt(Integer::intValue).average().orElse(0));
        int max = scores.stream().mapToInt(i->i).max().orElse(0);
        System.out.printf("Games=%d | avg=%d | max=%d | scores=%s%n", N, avg, max, scores);
        System.exit(0);
    }
}
