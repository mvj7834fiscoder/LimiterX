package com.learn.demo.controller;

import com.learn.demo.service.ClientRegistryService;
import com.learn.demo.utils.JwtFilter;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import java.time.Duration;

@Slf4j
@RestController
@RequestMapping("/home")
public class UserController {

    @Autowired
    public ClientRegistryService clientRegistryService;

    private final WebClient backendWebClient;
    private final ProxyManager<String> proxyManager;
    private final BucketConfiguration bucketConfiguration;

    public UserController(WebClient backendWebClient,ProxyManager<String> proxyManager) {
        this.backendWebClient = backendWebClient;
        this.proxyManager = proxyManager;

        // Blueprint config: Max 5 requests per 1 minute
        this.bucketConfiguration = BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1))))
                .build();
    }

    //Method used to call external API(Gateway)
    @GetMapping("/navigation")
    public ResponseEntity<String> callExternalAPI() {
        // 1. Extract username from JWT Security Context
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = (authentication != null) ? authentication.getName() : "anonymous";

        String redisKey = "rate:limit:user:" + username;
        boolean allowRequest = true;

        try {
            // 2. Fetch/Compute the bucket state tracking count from Docker Redis
            Bucket bucket = proxyManager.builder().build(redisKey, () -> bucketConfiguration);

            // 3. Consume a token
            if (!bucket.tryConsume(1)) {
                allowRequest = false;
            }
            log.info("Redis bucket check for user [{}]. Tokens remaining: {}", username, bucket.getAvailableTokens());

        } catch (Exception ex) {
            // FALLBACK: If Docker Redis is down/restarting, log warning and fail open
            log.error("CRITICAL: Redis is unreachable! Failing open to preserve service uptime. Error: {}", ex.getMessage());
            allowRequest = true;
        }

        // 4. Handle rate-limit breach
        if (!allowRequest) {
            log.warn("Rate limit breached for user [{}]. Sending HTTP 429.", username);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("{\"error\": \"Too Many Requests\", \"message\": \"Rate limit exceeded. Try again in a minute.\"}");
        }

        // 5. Proceed to External API call
        try {
            String response = backendWebClient.get()
                    .uri("/validation")
                    .header("Accept", "text/plain")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            log.error("Downstream WebClient link exception: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Gateway Integration Fallback: " + ex.getMessage());
        }
    }

    //Method used to generate JWT token
    @PostMapping("/generatetoken")
    public ResponseEntity<String> getToken(
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String clientSecret) {

        // 1. Log the incoming data presence to debug Postman formatting issues
        log.info(">>>> Received token generation request. clientId present: [{}], clientSecret present: [{}]",
                (clientId != null), (clientSecret != null));

        // 2. Catch missing parameters manually so you can see them in your logs
        if (clientId == null || clientSecret == null) {
            log.error("REJECTED: Missing required request parameters! Got clientId=[{}] and clientSecret=[{}]",
                    clientId, (clientSecret != null ? "PROTECTED" : "NULL"));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Bad Request: Both clientId and clientSecret parameters are required.");
        }

        // 3. Verify credentials
        if (!clientRegistryService.isValidClient(clientId, clientSecret)) {
            log.warn("⚠️ AUTHENTICATION FAILED: Invalid credentials matching properties for clientId: [{}]", clientId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Authentication Failed: Invalid credentials.");
        }

        // 4. Generate token
        log.info("✅ SUCCESS: Client [{}] verified. Generating JWT...", clientId);
        String token = JwtFilter.generateToken(clientId);

        return ResponseEntity.ok(token);
    }
}
