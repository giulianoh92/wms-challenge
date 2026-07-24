package io.tenoro.app.infra.config;

import io.tenoro.app.domain.port.inbound.UserService;
import io.tenoro.app.domain.port.outbound.UserRepository;
import io.tenoro.app.domain.service.UserDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfiguration {

    @Bean
    public UserService userService(UserRepository userRepository) {
        return new UserDomainService(userRepository);
    }
}
