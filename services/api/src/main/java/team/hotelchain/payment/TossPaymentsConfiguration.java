package team.hotelchain.payment;

import java.net.http.HttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TossPaymentsProperties.class)
class TossPaymentsConfiguration {
    @Bean
    @ConditionalOnProperty(name = "payment.provider", havingValue = "toss-test")
    TossPaymentsClient tossPaymentsClient(TossPaymentsProperties properties) {
        properties.requireTestConfiguration();
        return new TossPaymentsHttpClient(properties, HttpClient.newBuilder().build());
    }
}
