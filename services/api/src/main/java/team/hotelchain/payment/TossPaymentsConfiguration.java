package team.hotelchain.payment;

import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
