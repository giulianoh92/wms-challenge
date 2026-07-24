package io.tenoro.app.infra.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Configuration
public class EndpointPrinter implements ApplicationListener<ContextRefreshedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(EndpointPrinter.class);

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        RequestMappingHandlerMapping requestMappingHandlerMapping = event
                .getApplicationContext()
                .getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);

        Set<RequestMappingInfo> mappings = requestMappingHandlerMapping.getHandlerMethods().keySet();

        // Get all endpoints and sort them
        List<String> endpoints = new ArrayList<>();
        mappings.forEach(info -> {
            if (info != null && info.getPathPatternsCondition() != null) {
                info.getPathPatternsCondition().getPatterns().forEach(pattern -> {
                    String path = pattern.getPatternString();
                    // Add context path if it exists
                    if (contextPath != null && !contextPath.isEmpty()) {
                        path = contextPath + path;
                    }
                    endpoints.add(path);
                });
            }
        });
        endpoints.sort(String::compareTo);

        // Print all endpoints
        logger.info("\n\nAvailable REST endpoints:\n{}",
                   endpoints.stream()
                           .map(endpoint -> "  " + endpoint)
                           .reduce((a, b) -> a + "\n" + b)
                           .orElse("No endpoints available"));

        // Print Swagger and OpenAPI endpoints
        logger.info("\nAPI Documentation:\n  {}/documentation (Swagger UI)\n  {}/openapi (OpenAPI JSON)",
                   contextPath, contextPath);

        // Print Actuator endpoints
        logger.info("\nActuator endpoints:\n  {}/actuator/health\n  {}/actuator/info\n  {}/actuator/metrics",
                   contextPath, contextPath, contextPath);
    }
}
