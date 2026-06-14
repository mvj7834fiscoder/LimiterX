package com.learn.demo.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import org.apache.catalina.filters.RateLimitFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RateLimitFilterTest {

    @Mock
    private ProxyManager<String> proxyManager;

    @Mock
    private RemoteBucketBuilder<String> remoteBucketBuilder; // Explicit mock for the builder step

    @InjectMocks
    private RateLimitFilter rateLimitFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = mock(FilterChain.class);
    }

    @Test
    void shouldAllowRequest_WhenTokensAreAvailable() throws ServletException, IOException {
        // Arrange
        request.setRequestURI("/home/navigation");
        request.setAttribute("username", "serviceA");

        // Create a real operational bucket with 10 tokens
        Bucket realBucket = Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(10).refillGreedy(10, Duration.ofMinutes(1)).build())
                .build();

        // 🔴 CRUCIAL FIX: Cast the proxyManager return to a raw RemoteBucketBuilder.
        // This satisfies the compiler and matches Bucket4j's internal execution engine perfectly.
        doReturn(remoteBucketBuilder).when(proxyManager).builder();

        // Use a lenient stub to map the build method call directly to the real bucket instance
        lenient().doReturn(realBucket).when(remoteBucketBuilder).build(eq("serviceA"), any(java.util.function.Supplier.class));

        // Act
        rateLimitFilter.doFilter(request, response, filterChain);

        // Assert
        verify(filterChain, times(1)).doFilter(request, response);
        assertEquals(HttpStatus.OK.value(), response.getStatus());
    }

    @Test
    void shouldBlockRequest_WithHttp429_WhenRateLimitExceeded() throws ServletException, IOException {
        // Arrange
        request.setRequestURI("/home/navigation");
        request.setAttribute("username", "serviceA");

        // Create a real bucket and exhaust its tokens completely
        Bucket emptyBucket = Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(10).refillGreedy(10, Duration.ofMinutes(1)).build())
                .build();
        emptyBucket.tryConsume(10);

        // 🔴 CRUCIAL FIX: Do the same raw builder mapping here for the blocked scenario
        doReturn(remoteBucketBuilder).when(proxyManager).builder();
        lenient().doReturn(emptyBucket).when(remoteBucketBuilder).build(eq("serviceA"), any(java.util.function.Supplier.class));

        // Act
        rateLimitFilter.doFilter(request, response, filterChain);

        // Assert
        verify(filterChain, never()).doFilter(any(), any());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.getStatus());
        assertEquals("{\"error\": \"Too Many Requests\", \"message\": \"Rate limit exceeded. Try again in a minute.\"}",
                response.getContentAsString().trim());
    }
}