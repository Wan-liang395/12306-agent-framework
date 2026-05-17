package com.grace.train12306.biz.agentservice.config;

import com.grace.train12306.biz.agentservice.context.AgentRequestContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Feign RPC 调用拦截器
 * 负责在 Agent 内部微服务相互调用时，将本地 ThreadLocal 的用户身份挂载到 HTTP Header 中透传
 */
@Slf4j
@Configuration
public class AgentFeignInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String username = AgentRequestContext.getUsername();
        String userId = AgentRequestContext.getUserId();
        if (username != null && !username.isEmpty()) {
            template.header("username", username);
        }
        if (userId != null && !userId.isEmpty() && !userId.equals("default_user")) {
            template.header("userId", userId);
        }
        log.info("🌐 [Feign 拦截器] 已将用户上下文透传: username={}, userId={}", username, userId);
    }
}