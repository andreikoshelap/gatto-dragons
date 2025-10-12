package com.gatto.dragon.api;

import com.gatto.dragon.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GameClientTest {

    RestClient.Builder restClient;
    MockRestServiceServer server;
    GameClient client;

    @BeforeEach
    void setUp() {
        // baseUrl is arbitrary — the server will intercept calls in memory
        restClient = RestClient.builder().baseUrl("https://test.local");
        server = MockRestServiceServer.bindTo( restClient).build();
        client = new GameClient(restClient.build());
    }

    @Test
    void start_ok() {
        server.expect(once(), requestTo("https://test.local/api/v2/game/start"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                    {
                      "gameId":"G123",
                      "lives":3,
                      "gold":0,
                      "level":0,
                      "score":0,
                      "highScore":0,
                      "turn":0
                    }
                """, MediaType.APPLICATION_JSON));

        Game g = client.start();

        server.verify();
        assertThat(g.gameId()).isEqualTo("G123");
        assertThat(g.lives()).isEqualTo(3);
    }

    @Test
    void messages_ok_returnsList() {
        server.expect(once(), requestTo("https://test.local/api/v2/G123/messages"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                    [
                      {"adId":"AAAA1111","message":"help","reward":50,"expiresIn":5,"encrypted":false,"probability":"Sure thing"},
                      {"adId":"BBBB2222","message":"run","reward":120,"expiresIn":3,"encrypted":false,"probability":"Risky"}
                    ]
                """, MediaType.APPLICATION_JSON));

        List<Message> msgs = client.messages("G123");

        server.verify();
        assertThat(msgs).hasSize(2);
        assertThat(msgs.getFirst().adId()).isEqualTo("AAAA1111");
    }

    @Test
    void messages_410_returnsNull() {
        server.expect(once(), requestTo("https://test.local/api/v2/G999/messages"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.GONE).body("{\"status\":\"Game Over\"}").contentType(MediaType.APPLICATION_JSON));

        List<Message> msgs = client.messages("G999");

        server.verify();
        assertThat(msgs).isNull();
    }

    @Test
    void shop_ok_returnsItems() {
        server.expect(once(), requestTo("https://test.local/api/v2/G123/shop"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                    [
                      {"id":"P1","name":"Small pot","cost":80},
                      {"id":"S1","name":"Sword","cost":120}
                    ]
                """, MediaType.APPLICATION_JSON));

        List<ShopItem> items = client.shop("G123");

        server.verify();
        assertThat(items).extracting(ShopItem::id).containsExactly("P1","S1");
    }

    @Test
    void shop_410_returnsNull() {
        server.expect(once(), requestTo("https://test.local/api/v2/GEND/shop"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.GONE).body("{\"status\":\"Game Over\"}").contentType(MediaType.APPLICATION_JSON));

        List<ShopItem> items = client.shop("GEND");

        server.verify();
        assertThat(items).isNull();
    }

    @Test
    void solve_ok_success() {
        server.expect(once(), requestTo("https://test.local/api/v2/G123/solve/AAAA1111"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                    {
                      "lives":3,
                      "gold":50,
                      "score":50,
                      "highScore":50,
                      "turn":1,
                      "success":true
                    }
                """, MediaType.APPLICATION_JSON));

        SolveResult r = client.solve("G123", "AAAA1111");

        server.verify();
        assertThat(r).isNotNull();
        assertThat(r.success()).isTrue();
    }

    @Test
    void solve_400_returnsNull() {
        // important: the client builds URI via UriComponentsBuilder and trim+encode
        server.expect(once(), requestTo("https://test.local/api/v2/G123/solve/BAD!ID"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("<html>Bad Request</html>").contentType(MediaType.TEXT_HTML));

        SolveResult r = client.solve("G123", "BAD!ID");

        server.verify();
        assertThat(r).isNull();
    }

    @Test
    void solve_404_returnsNull() {
        server.expect(once(), requestTo("https://test.local/api/v2/G123/solve/NOTFOUND"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("no such ad"));

        SolveResult r = client.solve("G123", "NOTFOUND");

        server.verify();
        assertThat(r).isNull();
    }

    @Test
    void purchase_ok() {
        server.expect(once(), requestTo("https://test.local/api/v2/G123/shop/buy/P1"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                    {
                      "lives":2,
                      "gold":10,
                      "turn":5,
                      "shoppingSuccess":true
                    }
                """, MediaType.APPLICATION_JSON));

        PurchaseResult pr = client.purchase("G123", "P1");

        server.verify();
        assertThat(pr).isNotNull();
        assertThat(pr.shoppingSuccess()).isTrue();
        assertThat(pr.gold()).isEqualTo(10);
    }

    @Test
    void purchase_410_returnsNull() {
        server.expect(once(), requestTo("https://test.local/api/v2/GEND/shop/buy/P1"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.GONE).body("{\"status\":\"Game Over\"}").contentType(MediaType.APPLICATION_JSON));

        PurchaseResult pr = client.purchase("GEND", "P1");

        server.verify();
        assertThat(pr).isNull();
    }

    @Test
    void investigateReputation_ok() {
        server.expect(once(), requestTo("https://test.local/api/v2/G123/investigate/reputation"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                    { "people": 20, "state": -10, "underworld": 5 }
                """, MediaType.APPLICATION_JSON));

        Reputation rep = client.investigateReputation("G123");

        server.verify();
        assertThat(rep.people()).isEqualTo(20);
        assertThat(rep.state()).isEqualTo(-10);
        assertThat(rep.underworld()).isEqualTo(5);
    }

    @Test
    void investigateReputation_400_404_410_returnsNull() {
        // 400
        server.expect(once(), requestTo("https://test.local/api/v2/G1/investigate/reputation"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("bad req"));
        assertThat(client.investigateReputation("G1")).isNull();
        server.reset();
        // 404
        server.expect(once(), requestTo("https://test.local/api/v2/G2/investigate/reputation"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("nf"));
        assertThat(client.investigateReputation("G2")).isNull();
        server.reset();
        // 410
        server.expect(once(), requestTo("https://test.local/api/v2/G3/investigate/reputation"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.GONE).body("gone"));
        assertThat(client.investigateReputation("G3")).isNull();

        server.verify();
    }
}
