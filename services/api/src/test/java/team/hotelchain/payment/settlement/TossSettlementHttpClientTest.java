package team.hotelchain.payment.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import team.hotelchain.payment.TossPaymentEnvironment;
import team.hotelchain.payment.TossPaymentsProperties;

class TossSettlementHttpClientTest {
    private static final LocalDate DAY = LocalDate.of(2026, 9, 14);

    @Test
    void requests_one_sold_date_page_and_maps_fee_and_cancellation() {
        var http = new CapturingHttpClient(200, """
                [{"mId":"hotel-live","paymentKey":"pay-1","transactionKey":"txn-1",
                  "orderId":"order-1","currency":"KRW","method":"카드","amount":97000,
                  "fees":[{"type":"BASE","fee":2000},{"type":"ETC","fee":1000}],
                  "supplyAmount":2727,"vat":273,"payOutAmount":94000,
                  "approvedAt":"2026-09-14T10:00:00+09:00","soldDate":"2026-09-14",
                  "paidOutDate":"2026-09-21","cancel":{"cancelAmount":97000}}]
                """);
        var client = new TossSettlementHttpClient(liveProperties(), TossPaymentEnvironment.LIVE, http, new ObjectMapper());

        var page = client.fetch(DAY, 2, 1);

        assertThat(http.request.uri().getRawQuery()).isEqualTo(
                "startDate=2026-09-14&endDate=2026-09-14&dateType=soldDate&page=2&size=1");
        assertThat(http.request.timeout()).contains(Duration.ofSeconds(65));
        assertThat(http.request.headers().firstValue("Authorization")).hasValueSatisfying(v -> assertThat(v).startsWith("Basic "));
        assertThat(page.hasNext()).isTrue();
        assertThat(page.records()).singleElement().satisfies(record -> {
            assertThat(record.feeKrw()).isEqualTo(3000);
            assertThat(record.feeSupplyKrw()).isEqualTo(2727);
            assertThat(record.feeVatKrw()).isEqualTo(273);
            assertThat(record.cancellation()).isTrue();
        });
    }

    @Test
    void rejects_wrong_merchant_and_unsupported_method() {
        assertMalformed(single("foreign", "카드", 1000));
        assertMalformed(single("hotel-live", "가상계좌", 1000));
    }

    @Test
    void preserves_structural_fee_mismatch_for_reconciliation() {
        var client = new TossSettlementHttpClient(liveProperties(), TossPaymentEnvironment.LIVE,
                new CapturingHttpClient(200, single("hotel-live", "간편결제", 999)), new ObjectMapper());

        assertThat(client.fetch(DAY, 1, 500).records()).singleElement()
                .satisfies(record -> assertThat(record.payoutKrw()).isEqualTo(999));
    }

    @Test
    void accepts_test_environment_configuration_and_reports_environment() {
        var properties = new TossPaymentsProperties(
                "test_gck_fixture", "test_gsk_fixture", "hotel-test", "http://127.0.0.1:4000");

        var client = new TossSettlementHttpClient(properties, TossPaymentEnvironment.TEST,
                new CapturingHttpClient(200, single("hotel-test", "카드", 999)), new ObjectMapper());

        assertThat(client.environment()).isEqualTo(TossPaymentEnvironment.TEST);
        assertThat(client.fetch(DAY, 1, 500).records()).isNotEmpty();
    }

    @Test
    void rejects_keys_that_do_not_match_the_chosen_environment() {
        var properties = new TossPaymentsProperties(
                "test_gck_fixture", "test_gsk_fixture", "hotel-test", "http://127.0.0.1:4000");

        assertThatThrownBy(() -> new TossSettlementHttpClient(properties, TossPaymentEnvironment.LIVE,
                new CapturingHttpClient(200, single("hotel-test", "카드", 999)), new ObjectMapper()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void classifies_retryable_and_terminal_http_failures() {
        assertThatThrownBy(() -> client(429, "{}").fetch(DAY, 1, 500))
                .isInstanceOfSatisfying(TossSettlementClient.SettlementException.class,
                        error -> assertThat(error.retryable()).isTrue());
        assertThatThrownBy(() -> client(400, "{\"code\":\"INVALID_REQUEST\"}").fetch(DAY, 1, 500))
                .isInstanceOfSatisfying(TossSettlementClient.SettlementException.class,
                        error -> assertThat(error.retryable()).isFalse());
    }

    private void assertMalformed(String body) {
        assertThatThrownBy(() -> client(200, body).fetch(DAY, 1, 500))
                .isInstanceOfSatisfying(TossSettlementClient.SettlementException.class,
                        error -> assertThat(error.code()).isEqualTo("MALFORMED_RESPONSE"));
    }

    private TossSettlementHttpClient client(int status, String body) {
        return new TossSettlementHttpClient(liveProperties(), TossPaymentEnvironment.LIVE,
                new CapturingHttpClient(status, body), new ObjectMapper());
    }

    private TossPaymentsProperties liveProperties() {
        return new TossPaymentsProperties("live_gck_fixture", "live_gsk_fixture", "hotel-live", "https://hotel.example");
    }

    private String single(String merchant, String method, long payout) {
        return "[{\"mId\":\"" + merchant + "\",\"paymentKey\":\"pay-1\",\"transactionKey\":\"txn-1\","
                + "\"orderId\":\"order-1\",\"currency\":\"KRW\",\"method\":\"" + method + "\",\"amount\":1000,"
                + "\"fees\":[{\"type\":\"BASE\",\"fee\":10}],\"supplyAmount\":9,\"vat\":1,\"payOutAmount\":" + payout + ","
                + "\"approvedAt\":\"2026-09-14T10:00:00+09:00\",\"soldDate\":\"2026-09-14\",\"paidOutDate\":\"2026-09-21\"}]";
    }

    private static final class CapturingHttpClient extends HttpClient {
        private final int status;
        private final String body;
        private HttpRequest request;
        private CapturingHttpClient(int status, String body) { this.status = status; this.body = body; }
        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { return null; }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
        @Override public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
            this.request = request;
            @SuppressWarnings("unchecked") T responseBody = (T) body;
            return new HttpResponse<>() {
                @Override public int statusCode() { return status; }
                @Override public HttpRequest request() { return request; }
                @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
                @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
                @Override public T body() { return responseBody; }
                @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
                @Override public URI uri() { return request.uri(); }
                @Override public Version version() { return Version.HTTP_1_1; }
            };
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) { throw new UnsupportedOperationException(); }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) { throw new UnsupportedOperationException(); }
    }
}
