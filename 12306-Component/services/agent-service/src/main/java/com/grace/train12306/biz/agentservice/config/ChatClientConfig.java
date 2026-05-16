package com.grace.train12306.biz.agentservice.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
        // 在构建 ChatClient 时，默认挂载 ChatMemoryAdvisor
        return builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
    @Bean
    public ChatMemory chatMemory() {
        // 使用内存存储聊天记录，重启后失效
        return MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();
    }
}
