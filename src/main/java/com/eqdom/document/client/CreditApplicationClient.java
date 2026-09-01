package com.eqdom.document.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "credit-service", url = "${credit-service.url}")
public interface CreditApplicationClient {

    @GetMapping("/api/credit-applications/{id}")
    CreditApplicationDto getById(@PathVariable("id") Long id);
}
