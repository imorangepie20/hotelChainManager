package team.hotelchain.payment;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import team.hotelchain.payment.settlement.TossSettlementClient;
import team.hotelchain.payment.settlement.TossSettlementHttpClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TossPaymentsProperties.class)
class TossPaymentsConfiguration {
    @Bean
    @ConditionalOnExpression("'${payment.provider:fake}' == 'toss-test' or '${payment.provider:fake}' == 'toss-live'")
    TossPaymentEnvironment tossPaymentEnvironment(@Value("${payment.provider:fake}") String provider) {
        return TossPaymentEnvironment.from(provider);
    }

    @Bean
    @ConditionalOnExpression("'${payment.provider:fake}' == 'toss-test' or '${payment.provider:fake}' == 'toss-live'")
    TossPaymentsClient tossPaymentsClient(TossPaymentsProperties properties, TossPaymentEnvironment environment) {
        environment.requireConfiguration(properties);
        return new TossPaymentsHttpClient(properties, HttpClient.newBuilder().build(), environment);
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(name = "payment.provider", havingValue = "toss-live")
    ExecutorService tossSettlementExecutor() {
        return Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().name("toss-settlement-", 0).factory());
    }

    @Bean
    @ConditionalOnProperty(name = "payment.provider", havingValue = "toss-live")
    TossSettlementClient tossSettlementClient(TossPaymentsProperties properties,
            @Qualifier("tossSettlementExecutor") ExecutorService executor) {
        TossPaymentEnvironment.LIVE.requireConfiguration(properties);
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).executor(executor).build();
        return new TossSettlementHttpClient(properties, http, new ObjectMapper());
    }
}
