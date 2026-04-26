package com.example.payment.web.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class RootController {

    @GetMapping("/")
    public Map<String, Object> root() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("service", "payment-service");    //it’s the name of the service
        m.put("health", "/actuator/health");
        m.put("docs", "/swagger-ui.html");
        m.put("api", "/api/v1");
        return m;
    }
}
