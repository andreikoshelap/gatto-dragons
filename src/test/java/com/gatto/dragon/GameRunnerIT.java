package com.gatto.dragon;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@Testcontainers
@SpringBootTest
class GameRunnerIT {

    @Container
    static GenericContainer<?> wiremock = new GenericContainer<>("wiremock/wiremock:3.5.4")
            .withExposedPorts(8080);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        String baseUrl = "http://" + wiremock.getHost() + ":" + wiremock.getMappedPort(8080);
        // подставляем базовый URL для нашего GameClient
        r.add("dragon.api.base-url", () -> baseUrl);
    }

    @Test
    void playsOneGameEndToEnd() {
        // Настраиваем WireMock (через HTTP)
        WireMock.configureFor(wiremock.getHost(), wiremock.getMappedPort(8080));

        // /start
        stubFor(post(urlEqualTo("/start"))
                .willReturn(okJson("""
                  {"gameId":"G123","lives":3,"gold":100,"score":0,"highScore":0,"turn":1}
                """)));

        // /messages
        stubFor(get(urlPathEqualTo("/G123/messages"))
                .willReturn(okJson("""
                  [
                    {"adId":"A1","message":"...", "reward":50, "expiresIn":3, "probability":"quite likely", "encrypted":false}
                  ]
                """)));

        // /solve
        stubFor(post(urlPathEqualTo("/G123/solve"))
                .withQueryParam("adId", equalTo("A1"))
                .willReturn(okJson("""
                  {"success":true,"lives":3,"gold":150,"score":50}
                """)));

        // /shop (не понадобится, если живём >1)
        stubFor(get(urlPathEqualTo("/G123/shop"))
                .willReturn(okJson("[]")));

        // Тут вызываем GameRunner.playOne() из контекста
        // и проверяем, что получилаcь валидная финальная Game
        // (в реальном тесте - через @Autowired GameRunner)
    }
}
