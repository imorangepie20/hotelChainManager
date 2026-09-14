package team.hotelchain.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;

/** Toss 테스트 API의 결제 승인·권위 조회만 담당한다. 모든 불명확한 응답은 재조회 가능한 UNKNOWN으로 남긴다. */
public final class TossPaymentsHttpClient implements TossPaymentsClient {
    private static final URI API_ORIGIN = URI.create("https://api.tosspayments.com");
    private static final Set<String> TERMINAL_REJECTIONS = Set.of("CANCELED", "PARTIAL_CANCELED", "ABORTED", "EXPIRED");
    private final TossPaymentsProperties properties;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public TossPaymentsHttpClient(TossPaymentsProperties properties, HttpClient http) {
        this.properties = properties;
        this.http = http;
    }

    @Override
    public ProviderPayment confirm(ConfirmCommand command) {
        if (command == null || command.paymentKey() == null || command.paymentKey().isBlank() || command.orderId() == null
                || command.orderId().isBlank() || command.amountKrw() <= 0 || !"KRW".equals(command.currency())
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("결제 키, 주문번호, KRW 금액, Idempotency-Key가 필요합니다.");
        }
        return send("/v1/payments/confirm", body(command.paymentKey(), command.orderId(), command.amountKrw()), command.idempotencyKey(), false);
    }

    @Override
    public ProviderPayment lookup(String paymentKey) {
        if (paymentKey == null || paymentKey.isBlank()) throw new IllegalArgumentException("결제 키가 필요합니다.");
        return send("/v1/payments/" + paymentKey, null, null, true);
    }

    private ProviderPayment send(String path, String body, String idempotencyKey, boolean lookup) {
        properties.requireTestConfiguration();
        HttpRequest.Builder request = HttpRequest.newBuilder(API_ORIGIN.resolve(path)).timeout(Duration.ofSeconds(10))
                .header("Authorization", authorization()).header("Accept", "application/json");
        if (idempotencyKey != null) request.header("Idempotency-Key", idempotencyKey);
        if (body == null) request.GET(); else request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return map(response.statusCode(), response.body(), lookup);
        } catch (IOException exception) { return unknown("HTTP_IO"); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); return unknown("HTTP_INTERRUPTED"); }
    }

    private ProviderPayment map(int code, String raw, boolean lookup) {
        if (code >= 500 || (lookup && code >= 400)) return unknown(code >= 500 ? "HTTP_5XX" : "LOOKUP_UNCONFIRMED");
        try {
            JsonNode node = json.readTree(raw == null ? "{}" : raw);
            if (node == null || !node.isObject()) return unknown("MALFORMED_RESPONSE");
            if (code >= 400) return new ProviderPayment(null, null, 0, null, ProviderStatus.FAILED, null, text(node.path("code"), "HTTP_4XX"));
            String paymentKey = text(node.path("paymentKey"), null);
            String orderId = text(node.path("orderId"), null);
            long amount = node.path("totalAmount").isIntegralNumber() ? node.path("totalAmount").longValue() : 0;
            String currency = text(node.path("currency"), null);
            String status = text(node.path("status"), null);
            if ("DONE".equals(status) && valid(paymentKey) && valid(orderId) && amount > 0 && "KRW".equals(currency)
                    && (!node.has("mId") || properties.merchantAccount().equals(text(node.path("mId"), null)))) {
                return new ProviderPayment(paymentKey, orderId, amount, currency, ProviderStatus.DONE, text(node.path("transactionKey"), null), null);
            }
            if (TERMINAL_REJECTIONS.contains(status) && valid(text(node.path("code"), null))) {
                return new ProviderPayment(paymentKey, orderId, amount, currency, ProviderStatus.FAILED, text(node.path("transactionKey"), null), text(node.path("code"), "PAYMENT_REJECTED"));
            }
            return unknown("INCOMPLETE_DONE_RESPONSE");
        } catch (Exception exception) { return unknown("MALFORMED_RESPONSE"); }
    }

    private String authorization() { return "Basic " + Base64.getEncoder().encodeToString((properties.secretKey() + ":").getBytes(StandardCharsets.UTF_8)); }
    private String body(String paymentKey, String orderId, long amount) {
        try { return json.writeValueAsString(java.util.Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount)); }
        catch (Exception exception) { throw new IllegalStateException("Toss 결제 요청을 만들 수 없습니다.", exception); }
    }
    private ProviderPayment unknown(String code) { return new ProviderPayment(null, null, 0, null, ProviderStatus.UNKNOWN, null, code); }
    private boolean valid(String value) { return value != null && !value.isBlank(); }
    private String text(JsonNode node, String fallback) { return node != null && node.isTextual() && !node.textValue().isBlank() ? node.textValue() : fallback; }
}
