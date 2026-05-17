package com.grace.train12306.biz.agentservice.controller;

import com.grace.train12306.biz.agentservice.context.AgentRequestContext;
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
                               @RequestParam(value = "chatId", defaultValue = "default_user") String chatId,
                               @RequestParam(value = "username", required = false) String username) {
       String todayStr = LocalDate.now().toString();
        // 挂载上下文
       AgentRequestContext.setUsername(username);
        AgentRequestContext.setUserId(chatId);
       try {
        String response = chatClient.prompt()
                .system("你是一个12306智能出行助手。\n" +
                        "【核心时空】：今天是 " + todayStr + "。\n" +
                        "【行为规范】：\n" +
                        "1. 当用户想查票时，调用queryTicket工具查询车票。\n" +
                        "   - 只调用一次queryTicket，参数必须是有效的中文站名（如北京南、杭州东），绝对不能传null\n" +
                        "   - 用户说\"明天\"=" + LocalDate.now().plusDays(1) + "，\"后天\"=" + LocalDate.now().plusDays(2) + "\n" +
                        "   - 收到查询结果后直接展示，不要再次调用工具\n" +
                        "2. 当用户想买票时，先用queryTicket查票，再用bookTicket工具下单。\n" +
                        "   - bookTicket需要：trainId(从查票结果获取)、departure(出发站名)、arrival(到达站名)、passengerName(乘客姓名)、seatType(席别)\n" +
                        "   - 从用户对话中提取乘客姓名和席别\n" +
                        "3. 禁止编造二维码、链接或其他替代方案。\n" +
                        "4. 重要：每个工具最多调用一次，不要重复调用。")
                .user(message)
                .advisors(a -> a.param("chat_memory_conversation_id", chatId)
                        .param("chat_memory_retrieval_size", 10))
                .tools(ticketAgentTool, ticketBookingTool)
                .call()
                .content();

        return Results.success(response);
       } finally {
           AgentRequestContext.clear();
       }
    }
}