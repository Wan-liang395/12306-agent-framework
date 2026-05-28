package com.grace.train12306.biz.agentservice.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RailwayQaService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    // 构造函数：注入基础的 ChatClient 和 VectorStore
    public RailwayQaService(ChatClient.Builder builder, VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.chatClient = builder.build();
    }

    /**
     * 手工 RAG 核心链路：检索 -> 组装 Prompt -> 流式生成
     */
    public Flux<String> streamAnswer(String userQuestion) {

        // 第一步：拿着用户的问题，去向量库里做相似度检索 (底层原生的玩法)
        List<Document> matchedDocuments = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(userQuestion)
                        .topK(4)                       // 多捞几条，宁可错杀不可放过
                        .similarityThreshold(0.5)      // 降低匹配门槛
                        .build()
        );

        // 第二步：将检索到的文档内容提取出来，拼接成一段长文本
        String contextData = matchedDocuments.stream()
                .map(doc -> doc.getText())
                .collect(Collectors.joining("\n\n-----------------\n\n"));
        // 增加硬核监控日志！这行代码能让你清楚看到 RAG 到底搜出了什么！
        System.out.println("\n\n====== [RAG 调试] 本次检索到的上下文内容 ======\n" + contextData + "\n==============================================\n\n");
        // 第三步：动态构建系统提示词 (System Prompt)，把规章制度硬塞进去
        String dynamicSystemPrompt = "你是一个贴心的 12306 智能出行助手。请【必须且只能】根据下面提供的【检索到的规章制度】回答用户问题。\n" +
                "忘掉你之前学过的任何关于火车票的知识，一切以提供的规章制度为准。\n" +
                "如果规章制度中区分了“实名制”和“非实名制”，请分别向用户解释。\n\n" +
                "【检索到的规章制度】：\n" + contextData;

        // 第四步：携带动态上下文，向大模型发起流式请求
        return chatClient.prompt()
                .system(dynamicSystemPrompt)
                .user(userQuestion)
                .stream()
                .content();
    }
}