package com.eqdom.document.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "audit-service", url = "${audit-service.url}")
public interface AuditClient {

    @PostMapping("/api/audit-logs")
    void record(@RequestBody AuditEventRequest request);
}
