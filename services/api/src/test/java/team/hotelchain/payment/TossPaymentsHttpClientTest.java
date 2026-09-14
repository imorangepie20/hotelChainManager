package team.hotelchain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TossPaymentsHttpClientTest {

    @Test
    void rejectsLiveOrMissingKeys() {
        assertThatThrownBy(() -> properties("live_ck_x", "test_sk_x").requireTestConfiguration())
                .hasMessageContaining("test_");
        assertThatThrownBy(() -> properties("test_ck_x", "").requireTestConfiguration())
                .hasMessageContaining("test_");
    }

    @Test
    void confirmSendsStableKeyAndServerOrder() {
        CapturingHttpClient http = new CapturingHttpClient("""
                {"paymentKey":"pay","orderId":"order","totalAmount":120000,
                 "currency":"KRW","status":"DONE","transactionKey":"txn"}
                """);
        TossPaymentsHttpClient client = new TossPaymentsHttpClient(
                properties("test_ck_x", "test_sk_x"), http);

        TossPaymentsClient.ProviderPayment result = client.confirm(
                new TossPaymentsClient.ConfirmCommand("pay", "order", 120000, "KRW", "idem-1"));

        assertThat(result.status()).isEqualTo(TossPaymentsClient.ProviderStatus.DONE);
        assertThat(http.request.get().headers().firstValue("Idempotency-Key")).contains("idem-1");
        assertThat(http.request.get().bodyPublisher().orElseThrow()
                .contentLength()).isPositive();
        assertThat(http.body()).contains("\"orderId\":\"order\"").contains("\"amount\":120000");
    }

    @Test
    void mapsIncompleteDoneResponseToUnknown() {
        CapturingHttpClient http = new CapturingHttpClient("""
                {"paymentKey":"pay","orderId":"order","totalAmount":0,
                 "currency":"KRW","status":"DONE","transactionKey":"txn"}
                """);
        TossPaymentsHttpClient client = new TossPaymentsHttpClient(
                properties("test_ck_x", "test_sk_x"), http);

        TossPaymentsClient.ProviderPayment result = client.confirm(
                new TossPaymentsClient.ConfirmCommand("pay", "order", 120000, "KRW", "idem-1"));

        assertThat(result.status()).isEqualTo(TossPaymentsClient.ProviderStatus.UNKNOWN);
        assertThat(result.errorCode()).isEqualTo("INCOMPLETE_DONE_RESPONSE");
    }

    @ParameterizedTest
    @MethodSource("incompleteDoneResponses")
    void mapsNonContractDoneFieldsToUnknown(String responseBody) {
        TossPaymentsHttpClient client = new TossPaymentsHttpClient(
                properties("test_ck_x", "test_sk_x"), new CapturingHttpClient(responseBody));

        TossPaymentsClient.ProviderPayment result = client.confirm(
                new TossPaymentsClient.ConfirmCommand("pay", "order", 120000, "KRW", "idem-1"));

        assertThat(result.status()).isEqualTo(TossPaymentsClient.ProviderStatus.UNKNOWN);
        assertThat(result.errorCode()).isEqualTo("INCOMPLETE_DONE_RESPONSE");
    }

    private static Stream<Arguments> incompleteDoneResponses() {
        return Stream.of(
                Arguments.of(done("123", "\"order\"", "120000", "\"KRW\"")),
                Arguments.of(done("true", "\"order\"", "120000", "\"KRW\"")),
                Arguments.of(done("\"pay\"", "123", "120000", "\"KRW\"")),
                Arguments.of(done("\"pay\"", "false", "120000", "\"KRW\"")),
                Arguments.of(done(null, "\"order\"", "120000", "\"KRW\"")),
                Arguments.of("{\"paymentKey\":\"pay\",\"totalAmount\":120000,\"currency\":\"KRW\",\"status\":\"DONE\"}"),
                Arguments.of(done("\"   \"", "\"order\"", "120000", "\"KRW\"")),
                Arguments.of(done("\"pay\"", "\"\"", "120000", "\"KRW\"")),
                Arguments.of(done("\"pay\"", "\"order\"", "\"120000\"", "\"KRW\"")),
                Arguments.of(done("\"pay\"", "\"order\"", "1.5", "\"KRW\"")),
                Arguments.of(done("\"pay\"", "\"order\"", "-1", "\"KRW\"")),
                Arguments.of(done("\"pay\"", "\"order\"", "120000", "\"USD\"")));
    }

    private static String done(String paymentKey, String orderId, String amount, String currency) {
        String payment = paymentKey == null ? "" : "\"paymentKey\":" + paymentKey + ",";
        return "{" + payment + "\"orderId\":" + orderId + ",\"totalAmount\":" + amount
                + ",\"currency\":" + currency + ",\"status\":\"DONE\",\"transactionKey\":\"txn\"}";
    }

    private TossPaymentsProperties properties(String clientKey, String secretKey) {
        return new TossPaymentsProperties(clientKey, secretKey, "hotel-test", "http://127.0.0.1:4000");
    }

    private static final class CapturingHttpClient extends HttpClient {
        private final String responseBody;
        private final AtomicReference<HttpRequest> request = new AtomicReference<>();

        private CapturingHttpClient(String responseBody) {
            this.responseBody = responseBody;
        }

        String body() {
            BodyCollector collector = new BodyCollector();
            request.get().bodyPublisher().orElseThrow().subscribe(collector);
            return collector.result().join();
        }

        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { return null; }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            this.request.set(request);
            @SuppressWarnings("unchecked")
            T body = (T) responseBody;
            return new HttpResponse<>() {
                @Override public int statusCode() { return 200; }
                @Override public HttpRequest request() { return request; }
                @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
                @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
                @Override public T body() { return body; }
                @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
                @Override public URI uri() { return request.uri(); }
                @Override public Version version() { return Version.HTTP_1_1; }
            };
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            return CompletableFuture.completedFuture(send(request, handler));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return CompletableFuture.completedFuture(send(request, handler));
        }
    }

    private static final class BodyCollector implements Flow.Subscriber<java.nio.ByteBuffer> {
        private final CompletableFuture<String> result = new CompletableFuture<>();
        private final StringBuilder body = new StringBuilder();

        CompletableFuture<String> result() { return result; }
        @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
        @Override public void onNext(java.nio.ByteBuffer item) { body.append(StandardCharsets.UTF_8.decode(item)); }
        @Override public void onError(Throwable throwable) { result.completeExceptionally(throwable); }
        @Override public void onComplete() { result.complete(body.toString()); }
    }
}
