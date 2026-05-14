package com.grace.train12306.biz.agentservice.controller;

import com.grace.train12306.biz.agentservice.tool.TicketAgentTool;
import com.grace.train12306.framework.starter.convention.result.Result;
import com.grace.train12306.framework.starter.web.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentChatController {

    private final ChatClient chatClient;
    private final TicketAgentTool ticketAgentTool;

    @GetMapping("/chat")
    public Result<String> chat(@RequestParam("message") String message) {
        String response = chatClient.prompt()
                .system("你是一个精简的12306助手。拿到数据后，只播报前3个班次。班次超过10个时，提醒用户可以缩小查询范围。")
                .user(message)
                .tools(ticketAgentTool)
                .call()
                .content();

        return Results.success(response);
    }
}