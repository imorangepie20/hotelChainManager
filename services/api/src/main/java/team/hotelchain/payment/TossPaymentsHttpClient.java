package team.hotelchain.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
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

public final class TossPaymentsHttpClient implements TossPaymentsClient {
    private static final URI API_ORIGIN = URI.create("https://api.tosspayments.com");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Set<String> TERMINAL_REJECTION_STATUSES = Set.of(
            "CANCELED", "PARTIAL_CANCELED", "ABORTED", "EXPIRED");
    private static final Set<Integer> INDETERMINATE_LOOKUP_STATUSES = Set.of(401, 403, 429);

    private final TossPaymentsProperties properties;
    private final HttpClient http;
    private final ObjectMapper json;

    public TossPaymentsHttpClient(TossPaymentsProperties properties, HttpClient http) {
        this(properties, http, new ObjectMapper());
    }

    TossPaymentsHttpClient(TossPaymentsProperties properties, HttpClient http, ObjectMapper json) {
        this.properties = properties;
        this.http = http;
        this.json = json;
    }

    @Override
    public ProviderPayment confirm(ConfirmCommand command) {
        requireConfirm(command);
        return send("/v1/payments/confirm", requestBody(
                "paymentKey", command.paymentKey(), "orderId", command.orderId(), "amount", command.amountKrw()),
                command.idempotencyKey(), false, null);
    }

    @Override
    public ProviderPayment lookup(String paymentKey) {
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new IllegalArgumentException("결제 키가 필요합니다.");
        }
        return send("/v1/payments/" + paymentKey, null, null, true, null);
    }

    @Override
    public ProviderPayment cancel(CancelCommand command) {
        if (command == null || command.paymentKey() == null || command.paymentKey().isBlank()
                || command.amountKrw() <= 0 || command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("취소 결제 키, 금액, Idempotency-Key가 필요합니다.");
        }
        return send("/v1/payments/" + command.paymentKey() + "/cancel", requestBody(
                "cancelAmount", command.amountKrw(), "cancelReason", command.reason()), command.idempotencyKey(), false, command);
    }

    @Override
    public ProviderPayment lookupCancel(CancelCommand command) {
        return send("/v1/payments/" + command.paymentKey(), null, null, true, command);
    }

    private ProviderPayment send(String path, String requestBody, String idempotencyKey, boolean lookup, CancelCommand cancel) {
        properties.requireTestConfiguration();
        HttpRequest.Builder request = HttpRequest.newBuilder(API_ORIGIN.resolve(path))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", authorization())
                .header("Accept", "application/json");
        if (idempotencyKey != null) request.header("Idempotency-Key", idempotencyKey);
        if (requestBody == null) request.GET();
        else request.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return map(response.statusCode(), response.body(), lookup, cancel);
        } catch (IOException exception) {
            return unknown("HTTP_IO");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return unknown("HTTP_INTERRUPTED");
        }
    }

    private String authorization() {
        String credential = Base64.getEncoder().encodeToString((properties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));
        return "Basic " + credential;
    }

    private ProviderPayment map(int statusCode, String responseBody, boolean lookup, CancelCommand cancel) {
        if (statusCode >= 500) return unknown("HTTP_5XX");
        if (INDETERMINATE_LOOKUP_STATUSES.contains(statusCode)) return unknown("HTTP_" + statusCode);
        try {
            JsonNode body = json.readTree(responseBody == null ? "{}" : responseBody);
            if (body == null || !body.isObject()) return unknown("MALFORMED_RESPONSE");
            if (lookup && statusCode >= 400) return unknown("LOOKUP_UNCONFIRMED");
            if (cancel != null && statusCode >= 400 && (!validCode(textValue(body.path("code")))
                    || "ALREADY_CANCELED_PAYMENT".equals(textValue(body.path("code"))))) {
                return unknown("REFUND_UNCONFIRMED");
            }
            if (statusCode >= 400) return new ProviderPayment(null, null, 0, null,
                    ProviderStatus.FAILED, null, safeCode(body.path("code").asText(null), "HTTP_4XX"));
            String paymentKey = textValue(body.path("paymentKey"));
            String orderId = textValue(body.path("orderId"));
            JsonNode amount = body.path("totalAmount");
            long amountKrw = integralAmount(amount);
            String currency = textValue(body.path("currency"));
            String providerStatus = textValue(body.path("status"));
            boolean done = "DONE".equals(providerStatus);
            // 상점 키로 인증한 응답이 권위다. 선택적 mId가 있으면 설정과 추가 대조한다.
            if (body.has("mId") && !properties.merchantAccount().equals(textValue(body.path("mId")))) {
                return unknown("MERCHANT_MISMATCH");
            }
            if (cancel != null) {
                if (!cancel.paymentKey().equals(paymentKey) || !hasText(orderId) || !"KRW".equals(currency)
                        || !("CANCELED".equals(providerStatus) || "PARTIAL_CANCELED".equals(providerStatus))) {
                    return unknown("REFUND_UNCONFIRMED");
                }
                JsonNode match = null;
                for (JsonNode event : body.path("cancels")) {
                    if (integralAmount(event.path("cancelAmount")) == cancel.amountKrw()
                            && "DONE".equals(textValue(event.path("cancelStatus")))
                            && cancel.reason().equals(textValue(event.path("cancelReason")))
                            && hasText(textValue(event.path("transactionKey")))) {
                        if (match != null) return unknown("AMBIGUOUS_REFUND");
                        match = event;
                    }
                }
                return match == null ? unknown("REFUND_UNCONFIRMED") : new ProviderPayment(paymentKey, orderId,
                        cancel.amountKrw(), currency, ProviderStatus.DONE, textValue(match.path("transactionKey")), null);
            }
            if (done && (!hasText(paymentKey) || !hasText(orderId) || amountKrw <= 0 || !"KRW".equals(currency))) {
                return unknown("INCOMPLETE_DONE_RESPONSE");
            }
            if (!done && (providerStatus == null || !TERMINAL_REJECTION_STATUSES.contains(providerStatus)
                    || !validCode(textValue(body.path("code"))))) {
                return unknown("INCOMPLETE_DONE_RESPONSE");
            }
            ProviderStatus status = done ? ProviderStatus.DONE : ProviderStatus.FAILED;
            return new ProviderPayment(paymentKey, orderId, amountKrw, currency, status,
                    textValue(body.path("transactionKey")),
                    status == ProviderStatus.DONE ? null : textValue(body.path("code")));
        } catch (JsonProcessingException exception) {
            return unknown("MALFORMED_RESPONSE");
        }
    }

    private ProviderPayment unknown(String errorCode) {
        return new ProviderPayment(null, null, 0, null, ProviderStatus.UNKNOWN, null, errorCode);
    }

    private String safeCode(String code, String fallback) {
        return validCode(code) ? code : fallback;
    }

    private boolean validCode(String code) {
        return code != null && code.matches("[A-Z0-9_]{1,80}");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String textValue(JsonNode node) {
        return node.isTextual() ? node.textValue() : null;
    }

    private long integralAmount(JsonNode node) {
        return node.isIntegralNumber() && node.canConvertToLong() ? node.longValue() : 0;
    }

    private String requestBody(Object... fields) {
        try {
            java.util.LinkedHashMap<String, Object> body = new java.util.LinkedHashMap<>();
            for (int index = 0; index < fields.length; index += 2) body.put((String) fields[index], fields[index + 1]);
            return json.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Toss 결제 요청을 만들 수 없습니다.", exception);
        }
    }

    private void requireConfirm(ConfirmCommand command) {
        if (command == null || command.paymentKey() == null || command.paymentKey().isBlank()
                || command.orderId() == null || command.orderId().isBlank() || command.amountKrw() <= 0
                || !"KRW".equals(command.currency()) || command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("결제 키, 주문번호, KRW 금액, Idempotency-Key가 필요합니다.");
        }
    }
}
