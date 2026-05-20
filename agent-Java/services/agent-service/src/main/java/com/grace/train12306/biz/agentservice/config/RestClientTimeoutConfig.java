package com.grace.train12306.biz.agentservice.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

/**
 * 强行修改 Spring AI 底层网络请求的超时时间
 * 解决大模型思考超过 10 秒就被强制掐断连接的 Bug
 */
@Configuration
public class RestClientTimeoutConfig {

    @Bean
    public RestClientCustomizer restClientCustomizer() {
        return builder -> {
            // 直接改用 Spring 原生自带的 SimpleClientHttpRequestFactory
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

            // 连接超时设为 60 秒
            factory.setConnectTimeout(Duration.ofSeconds(60));
            // 读取超时（大模型推理时间）撑大到 120 秒！
            factory.setReadTimeout(Duration.ofSeconds(120));

            builder.requestFactory(factory);
        };
    }
}