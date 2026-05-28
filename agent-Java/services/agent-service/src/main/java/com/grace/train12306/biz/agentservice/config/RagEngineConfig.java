package com.grace.train12306.biz.agentservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Slf4j
@Configuration
public class RagEngineConfig {
    @Value("classpath:docs/12306_rules.txt")
    private Resource rulesResource;

    /**
     * 这里使用 SimpleVectorStore 做内存演示。
     * 如果要切换为生产级 Redis，只需引入 spring-ai-redis 依赖，并在此处注入 RedisVectorStore 即可。
     */
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();

        try {
            log.info("============== 开始加载 12306 铁路规章制度文档 ==============");
            // 1. 读取本地文本源
            TextReader textReader = new TextReader(rulesResource);
            List<Document> documents = textReader.get();

            // 2. 文本切片器 (每个 Chunk 大小 300 Token，重叠 50 Token 防止上下文割裂)
            TokenTextSplitter splitter = new TokenTextSplitter(300, 50, 5, 10000, true);
            List<Document> splitDocuments = splitter.apply(documents);

            // 3. 自动触发 Embedding 并写入本地向量库
            vectorStore.add(splitDocuments);
            log.info("============== 成功预热并将 {} 个文本切片写入向量库 ==============", splitDocuments.size());

        } catch (Exception e) {
            log.error("RAG 向量库初始化失败", e);
        }
        return vectorStore;
    }
}
