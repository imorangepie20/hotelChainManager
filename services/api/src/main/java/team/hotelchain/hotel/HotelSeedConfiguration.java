package team.hotelchain.hotel;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RoomTypeSeedProperties.class)
class HotelSeedConfiguration {
}
