package com.eqdom.document.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "notification-service", url = "${notification-service.url}")
public interface NotificationClient {

    @PostMapping("/api/notifications")
    void create(@RequestBody CreateNotificationRequest request);
}
