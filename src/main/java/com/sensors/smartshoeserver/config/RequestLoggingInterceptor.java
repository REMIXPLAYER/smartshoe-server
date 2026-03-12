package com.sensors.smartshoeserver.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 请求日志拦截器
 * 记录所有HTTP请求的详细信息
 */
@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RequestLoggingInterceptor.class);
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 请求开始时间存储在request属性中
    private static final String START_TIME_ATTRIBUTE = "requestStartTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 记录请求开始时间
        request.setAttribute(START_TIME_ATTRIBUTE, System.currentTimeMillis());

        String clientIp = getClientIpAddress(request);
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String userAgent = request.getHeader("User-Agent");
        String token = request.getHeader("Authorization");

        String fullUrl = queryString != null ? uri + "?" + queryString : uri;

        logger.info("[REQUEST] [{}] {} {} - Client: {} - Token: {}",
                LocalDateTime.now().format(formatter),
                method,
                fullUrl,
                clientIp,
                token != null ? "Present" : "None"
        );

        if (userAgent != null) {
            logger.debug("[REQUEST] User-Agent: {}", userAgent);
        }

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        // 可以在这里记录响应信息
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        Long startTime = (Long) request.getAttribute(START_TIME_ATTRIBUTE);
        long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;

        String method = request.getMethod();
        String uri = request.getRequestURI();
        int status = response.getStatus();

        String statusIndicator = status >= 200 && status < 300 ? "✓" :
                                status >= 400 ? "✗" : "⚠";

        if (ex != null) {
            logger.error("[RESPONSE] [{} {}] Status: {} {} - Duration: {}ms - ERROR: {}",
                    method,
                    uri,
                    status,
                    statusIndicator,
                    duration,
                    ex.getMessage()
            );
        } else {
            logger.info("[RESPONSE] [{} {}] Status: {} {} - Duration: {}ms",
                    method,
                    uri,
                    status,
                    statusIndicator,
                    duration
            );
        }
    }

    /**
     * 获取客户端真实IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 如果有多个IP，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
