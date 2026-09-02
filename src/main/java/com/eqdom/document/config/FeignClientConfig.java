package com.eqdom.document.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import feign.RequestInterceptor;

@Configuration
public class FeignClientConfig {

    @Value("${internal.service.key:}")
    private String internalServiceKey;

    @Bean
    public RequestInterceptor authForwardingInterceptor() {
        return requestTemplate -> {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                String authorization = attributes.getRequest().getHeader("Authorization");
                if (authorization != null) {
                    requestTemplate.header("Authorization", authorization);
                }
            }
            // Identifies this call as a trusted internal service-to-service call, independent of
            // whichever end-user's JWT (if any) is being forwarded above. notification-service and
            // audit-service accept this instead of requiring the forwarded caller to hold a staff
            // role, since events must be recorded/notified for CLIENT-initiated actions too.
            if (StringUtils.hasText(internalServiceKey)) {
                requestTemplate.header("X-Internal-Key", internalServiceKey);
            }
        };
    }
}
