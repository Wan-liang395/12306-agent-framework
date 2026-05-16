package com.grace.train12306.biz.agentservice.controller;

import com.grace.train12306.biz.agentservice.tool.TicketAgentTool;
import com.grace.train12306.framework.starter.convention.result.Result;
import com.grace.train12306.framework.starter.web.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;


@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentChatController {

    private final ChatClient chatClient;
    private final TicketAgentTool ticketAgentTool;

    @GetMapping("/chat")
    public Result<String> chat(@RequestParam("message") String message,
                               @RequestParam(value = "chatId", defaultValue = "default_user") String chatId) {
       String todayStr = LocalDate.now().toString();
        String response = chatClient.prompt()
                .system("你是一个专业的12306助手。拿到数据后，请严格按照返回的 departureTime（出发）、arrivalTime（到达）和 duration（历时）进行播报，切勿自行推算时差。")
                .user(message)
                .advisors(a -> a.param("chat_memory_conversation_id", chatId)
                        .param("chat_memory_retrieval_size", 10))
                .tools(ticketAgentTool)
                .call()
                .content();

        return Results.success(response);
    }
}