package io.tenoro.app.infra.config;

import io.tenoro.app.domain.port.inbound.LocationService;
import io.tenoro.app.domain.port.inbound.StockService;
import io.tenoro.app.domain.port.inbound.UserService;
import io.tenoro.app.domain.port.outbound.InventoryRepository;
import io.tenoro.app.domain.port.outbound.LocationRepository;
import io.tenoro.app.domain.port.outbound.UserRepository;
import io.tenoro.app.domain.service.LocationDomainService;
import io.tenoro.app.domain.service.StockDomainService;
import io.tenoro.app.domain.service.UserDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfiguration {

    @Bean
    public UserService userService(UserRepository userRepository) {
        return new UserDomainService(userRepository);
    }

    @Bean
    public LocationService locationService(LocationRepository locationRepository) {
        return new LocationDomainService(locationRepository);
    }

    @Bean
    public StockService stockService(InventoryRepository inventoryRepository, LocationRepository locationRepository) {
        return new StockDomainService(inventoryRepository, locationRepository);
    }
}
