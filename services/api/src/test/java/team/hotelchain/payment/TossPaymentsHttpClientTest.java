package team.hotelchain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class TossPaymentsHttpClientTest {
    private static final TossPaymentsProperties PROPERTIES = new TossPaymentsProperties(
            "test_ck_fixture", "test_sk_fixture", "hotel-test", "http://127.0.0.1:4000");

    @Test
    void confirmSendsIdempotentServerOrderAndMapsDone() {
        CapturingHttpClient http = new CapturingHttpClient(200, """
                {"paymentKey":"pay","orderId":"order","totalAmount":120000,
                 "currency":"KRW","status":"DONE","transactionKey":"transaction"}
                """);

        var result = new TossPaymentsHttpClient(PROPERTIES, http).confirm(command());

        assertThat(result.status()).isEqualTo(TossPaymentsClient.ProviderStatus.DONE);
        assertThat(http.request.headers().firstValue("Idempotency-Key")).contains("idem-1");
        assertThat(http.request.uri()).isEqualTo(URI.create("https://api.tosspayments.com/v1/payments/confirm"));
        assertThat(http.request.bodyPublisher().orElseThrow().contentLength()).isPositive();
    }

    @Test
    void malformedDoneResponseStaysUnknown() {
        var result = new TossPaymentsHttpClient(PROPERTIES, new CapturingHttpClient(200,
                "{\"paymentKey\":\"pay\",\"orderId\":\"order\",\"totalAmount\":0,\"currency\":\"KRW\",\"status\":\"DONE\"}"))
                .confirm(command());

        assertThat(result.status()).isEqualTo(TossPaymentsClient.ProviderStatus.UNKNOWN);
        assertThat(result.errorCode()).isEqualTo("INCOMPLETE_DONE_RESPONSE");
    }

    @Test
    void explicitProviderRejectionIsFailedButLookupUncertaintyIsUnknown() {
        var failed = new TossPaymentsHttpClient(PROPERTIES, new CapturingHttpClient(400, "{\"code\":\"INVALID_PAYMENT_AMOUNT\"}"))
                .confirm(command());
        var unknown = new TossPaymentsHttpClient(PROPERTIES, new CapturingHttpClient(401, "{\"code\":\"UNAUTHORIZED\"}"))
                .lookup("pay");

        assertThat(failed.status()).isEqualTo(TossPaymentsClient.ProviderStatus.FAILED);
        assertThat(unknown.status()).isEqualTo(TossPaymentsClient.ProviderStatus.UNKNOWN);
    }

    @Test
    void ioFailureAnd_liveKeyNeverProduceApproval() {
        var unknown = new TossPaymentsHttpClient(PROPERTIES, new CapturingHttpClient(new IOException("lost"))).lookup("pay");
        assertThat(unknown.status()).isEqualTo(TossPaymentsClient.ProviderStatus.UNKNOWN);
        assertThatThrownBy(() -> new TossPaymentsHttpClient(new TossPaymentsProperties(
                "live_ck_forbidden", "test_sk_fixture", "hotel-test", "http://127.0.0.1:4000"),
                new CapturingHttpClient(200, "{}")).confirm(command())).isInstanceOf(IllegalStateException.class);
    }

    private static TossPaymentsClient.ConfirmCommand command() {
        return new TossPaymentsClient.ConfirmCommand("pay", "order", 120000, "KRW", "idem-1");
    }

    private static final class CapturingHttpClient extends HttpClient {
        private final int status;
        private final String body;
        private final IOException failure;
        private HttpRequest request;

        CapturingHttpClient(int status, String body) { this.status = status; this.body = body; this.failure = null; }
        CapturingHttpClient(IOException failure) { this.status = 0; this.body = null; this.failure = failure; }
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
            if (failure != null) throw failure;
            @SuppressWarnings("unchecked") T responseBody = (T) body;
            return new HttpResponse<>() {
                @Override public int statusCode() { return status; }
                @Override public HttpRequest request() { return request; }
                @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
                @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (left, right) -> true); }
                @Override public T body() { return responseBody; }
                @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
                @Override public URI uri() { return request.uri(); }
                @Override public Version version() { return Version.HTTP_1_1; }
            };
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            try { return CompletableFuture.completedFuture(send(request, handler)); }
            catch (IOException exception) { return CompletableFuture.failedFuture(exception); }
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler, HttpResponse.PushPromiseHandler<T> pushHandler) {
            return sendAsync(request, handler);
        }
    }
}
