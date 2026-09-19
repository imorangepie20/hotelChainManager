package team.hotelchain.payment.settlement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Set;
import team.hotelchain.payment.TossPaymentsProperties;
import team.hotelchain.payment.TossPaymentEnvironment;

public final class TossSettlementHttpClient implements TossSettlementClient {
    private static final URI API = URI.create("https://api.tosspayments.com/v1/settlements");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(65);
    private static final Set<String> METHODS = Set.of("카드", "간편결제");
    private final TossPaymentsProperties properties;
    private final TossPaymentEnvironment environment;
    private final HttpClient http;
    private final ObjectMapper json;

    public TossSettlementHttpClient(TossPaymentsProperties properties, TossPaymentEnvironment environment,
            HttpClient http, ObjectMapper json) {
        environment.requireConfiguration(properties);
        this.properties = properties;
        this.environment = environment;
        this.http = http;
        this.json = json;
    }

    public TossPaymentEnvironment environment() {
        return environment;
    }

    @Override
    public SettlementPage fetch(LocalDate soldDate, int page, int size) {
        if (soldDate == null || page < 1 || size < 1 || size > 5000) {
            throw new IllegalArgumentException("정산 조회 날짜와 페이지 범위가 올바르지 않습니다.");
        }
        String date = encode(soldDate.toString());
        URI uri = URI.create(API + "?startDate=" + date + "&endDate=" + date
                + "&dateType=soldDate&page=" + page + "&size=" + size);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", authorization())
                .header("Accept", "application/json")
                .GET().build();
        try {
            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 429 || response.statusCode() >= 500) {
                throw failure("HTTP_" + response.statusCode(), true);
            }
            if (response.statusCode() >= 400) throw failure("HTTP_4XX", false);
            JsonNode body = json.readTree(response.body());
            if (body == null || !body.isArray()) throw failure("MALFORMED_RESPONSE", false);
            var records = new ArrayList<SettlementRecord>();
            for (JsonNode item : body) records.add(map(item, soldDate));
            return new SettlementPage(records, records.size() == size);
        } catch (SettlementException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SettlementException("HTTP_INTERRUPTED", true, exception);
        } catch (IOException exception) {
            throw new SettlementException("HTTP_IO", true, exception);
        } catch (RuntimeException exception) {
            throw new SettlementException("MALFORMED_RESPONSE", false, exception);
        }
    }

    private SettlementRecord map(JsonNode item, LocalDate requestedDate) {
        String merchant = text(item, "mId");
        String paymentKey = text(item, "paymentKey");
        String transactionKey = text(item, "transactionKey");
        String orderId = text(item, "orderId");
        String currency = text(item, "currency");
        String method = text(item, "method");
        LocalDate soldDate = LocalDate.parse(text(item, "soldDate"));
        if (!properties.merchantAccount().equals(merchant) || !"KRW".equals(currency)
                || !METHODS.contains(method) || !requestedDate.equals(soldDate)) {
            throw failure("MALFORMED_RESPONSE", false);
        }
        long fee = 0;
        JsonNode fees = item.path("fees");
        if (!fees.isArray()) throw failure("MALFORMED_RESPONSE", false);
        for (JsonNode row : fees) {
            long value = integral(row.path("fee"));
            if (value < 0) throw failure("MALFORMED_RESPONSE", false);
            fee = Math.addExact(fee, value);
        }
        return new SettlementRecord(merchant, paymentKey, transactionKey, orderId, currency, method,
                integral(item.path("amount")), fee, nonNegative(item.path("supplyAmount")),
                nonNegative(item.path("vat")), integral(item.path("payOutAmount")),
                Instant.parse(text(item, "approvedAt")), soldDate,
                LocalDate.parse(text(item, "paidOutDate")), !item.path("cancel").isMissingNode()
                        && !item.path("cancel").isNull());
    }

    private long nonNegative(JsonNode node) {
        long value = integral(node);
        if (value < 0) throw failure("MALFORMED_RESPONSE", false);
        return value;
    }

    private long integral(JsonNode node) {
        if (!node.isIntegralNumber() || !node.canConvertToLong()) throw failure("MALFORMED_RESPONSE", false);
        return node.longValue();
    }

    private String text(JsonNode item, String field) {
        JsonNode node = item.path(field);
        if (!node.isTextual() || node.textValue().isBlank()) throw failure("MALFORMED_RESPONSE", false);
        return node.textValue();
    }

    private String authorization() {
        return "Basic " + Base64.getEncoder().encodeToString(
                (properties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private SettlementException failure(String code, boolean retryable) {
        return new SettlementException(code, retryable);
    }
}
