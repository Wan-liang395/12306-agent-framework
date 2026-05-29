package com.grace.train12306.biz.agentservice.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Slf4j
@Service
public class PlannerAgent {
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PlannerAgent(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /**
     * 规划师核心方法：分析用户意图，决定走哪条链路
     */
    public String analyzeIntent(String userMessage,String chatId, ChatMemory chatMemory) {
        String historyStr = "无";
        if (chatMemory != null) {
            // 1. 获取该对话目前所有的历史记录数量
            int totalSize = chatMemory.get(chatId).size();

            // 2. 获取全部记录，利用 Stream 的 skip 手动跳过前面的旧消息，只保留最后 2 条
            historyStr = chatMemory.get(chatId).stream()
                    .skip(Math.max(0, totalSize - 2))
                    .map(m -> m.getText())
                    .collect(Collectors.joining(" | "));
        }
        String systemPrompt = "你是一个 12306 高级任务规划师（Planner）。你的任务是分析用户的输入，判断用户的真实意图。\n" +
                "请严格按照以下分类进行判断：\n" +
                "1. [QA]: 用户在询问铁路规章制度、退票规则、儿童票政策、行李政策等知识类问题。\n" +
                "2. [BOOKING]: 用户要求查询车票、购买车票、或者正在进行购票流程。\n" +
                "3. [CHAT]: 普通的日常寒暄（如：你好、谢谢）。\n\n" +
                "你【必须且只能】返回一个合法的 JSON 对象，绝对不要返回任何多余的 Markdown 标记(如 ```json) 或解释文字。\n" +
                "JSON 格式如下：\n" +
                "{\"intent\": \"QA 或 BOOKING 或 CHAT\", \"reasoning\": \"简短的判断理由\"}";

        try {
            // 调用大模型进行意图推理
            String jsonResponse = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .call()
                    .content();

            // 清理可能带有的 markdown 标记 (容错处理)
            jsonResponse = jsonResponse.replace("```json", "").replace("```", "").trim();
            log.info("====== [Planner 思考过程] ======\n{}", jsonResponse);

            // 解析 JSON 并返回意图
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            return rootNode.get("intent").asText().toUpperCase();

        } catch (Exception e) {
            log.error("⚠️ Planner 意图识别失败，默认降级为 BOOKING 链路", e);
            return "BOOKING"; // 容错：如果解析失败，默认走买票/查票链路
        }
    }
}
