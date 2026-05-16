package com.grace.train12306.biz.agentservice.controller;

import com.grace.train12306.biz.agentservice.tool.TicketAgentTool;
import com.grace.train12306.biz.agentservice.tool.TicketBookingTool;
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
    private final TicketBookingTool ticketBookingTool;

    @GetMapping("/chat")
    public Result<String> chat(@RequestParam("message") String message,
                               @RequestParam(value = "chatId", defaultValue = "default_user") String chatId) {
       String todayStr = LocalDate.now().toString();
        String response = chatClient.prompt()
                .system("你是一个完全闭环的12306智能出行Agent。\n" +
                        "【核心时空】：今天是 " + todayStr + "。\n" +
                        "【行为规范】：\n" +
                        "1. 当用户信息不全时（如只想买票但没说车次或席别），优先调用查票工具展示列表，并引导用户确认车次、乘客和座位等级。\n" +
                        "2. 一旦购票三要素（车次、乘车人、席别）齐全，请立刻调用预订工具进行后台下单锁票。下单成功后，请优雅地播报订单号。")
                .user(message)
                .advisors(a -> a.param("chat_memory_conversation_id", chatId)
                        .param("chat_memory_retrieval_size", 10))
                .tools(ticketAgentTool,ticketBookingTool)
                .call()
                .content();

        return Results.success(response);
    }
}