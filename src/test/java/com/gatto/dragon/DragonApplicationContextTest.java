package com.gatto.dragon;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = DragonApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class DragonApplicationContextTest {

    @Test
    void contextLoads() {
        assertThat(true).isTrue();
    }
}
