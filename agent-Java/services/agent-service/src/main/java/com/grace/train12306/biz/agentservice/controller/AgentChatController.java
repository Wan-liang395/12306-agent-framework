package com.grace.train12306.biz.agentservice.controller;

import com.grace.train12306.biz.agentservice.agents.PlannerAgent;
import com.grace.train12306.biz.agentservice.context.AgentRequestContext;
import com.grace.train12306.biz.agentservice.rag.RailwayQaService;
import com.grace.train12306.biz.agentservice.tool.TicketAgentTool;
import com.grace.train12306.biz.agentservice.tool.TicketBookingTool;
import com.grace.train12306.framework.starter.convention.result.Result;
import com.grace.train12306.framework.starter.web.Results;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.LocalDate;


@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentChatController {

    private final ChatClient chatClient;
    private final TicketAgentTool ticketAgentTool;
    private final TicketBookingTool ticketBookingTool;

    // 注入我们新写的两大大脑
    private final PlannerAgent plannerAgent;
    private final RailwayQaService railwayQaService;
    private final ChatMemory chatMemory;

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<String>> chat(@RequestParam("message") String message,
                             @RequestParam(value = "chatId", defaultValue = "default_user") String chatId,
                             @RequestParam(value = "username", required = false) String username) {

        // 1. 挂载微服务上下文
        AgentRequestContext.setUsername(username);
        AgentRequestContext.setUserId(chatId);

        // 2. 规划师 (Planner) 开始工作，识别用户意图
        String intent = plannerAgent.analyzeIntent(message, chatId, chatMemory);
        log.info("🚥 [中央大脑路由分发] 最终决定走 [{}] 链路", intent);

        Flux<String> responseStream;

        // 3. 多智能体协作 (Multi-Agent Routing)
        switch (intent) {
            case "QA":
                // 走右脑：RAG 知识库链路
                responseStream = railwayQaService.streamAnswer(message);
                break;
            case "BOOKING":
            default:
                // 走左脑：工具箱与购票链路 (保留你原有的 Prompt 与上下文记忆)
                String todayStr = LocalDate.now().toString();
                String currentUsername = (username != null && !username.isEmpty()) ? username : "未登录";
                String currentUserId = (chatId != null && !chatId.isEmpty()) ? chatId : "default_user";

                responseStream = chatClient.prompt()
                        .system("你是一个12306智能出行助手。\n" +
                                "【核心时空】：今天是 " + todayStr + "。\n" +
                                "【当前登录账号】：当前用户的 username 是 [" + currentUsername + "]，userId 是 [" + currentUserId + "]。在调用 bookTicket 工具时，必须将这两个参数准确透传。\n" +
                                "【行为规范】：\n" +
                                "1. 当用户想查票时，调用queryTicket工具查询车票。\n" +
                                "   - 只调用一次queryTicket，参数必须是有效的中文站名（如北京南、杭州东），绝对不能传null\n" +
                                "   - 用户说\"明天\"=" + LocalDate.now().plusDays(1) + "，\"后天\"=" + LocalDate.now().plusDays(2) + "\n" +
                                "   - 收到查询结果后直接展示，不要再次调用工具\n" +
                                "2. 当用户想买票时，先用queryTicket查票，再用bookTicket工具下单。\n" +
                                "   - bookTicket需要：trainId(从查票结果获取)、departure(出发站名)、arrival(到达站名)、passengerName(乘客姓名)、seatType(席别)\n" +
                                "   - 从用户对话中提取乘客姓名和席别\n" +
                                "3. 禁止编造 trainId，绝对不能传如 G123 等虚假参数。\n" +
                                "4. 禁止编造二维码、链接或其他替代方案。\n" +
                                "5.【核心代买规则】：12306 支持为他人代买车票。提取 passengerName 参数时，必须严格使用用户指定的乘车人（如用户说给金来买，就填金来），绝对禁止将其篡改为登录账号名！同时，绝对禁止将购票失败的原因归咎于账号名与乘车人不一致!\n"+
                                "6. 重要：每个工具最多调用一次，不要重复调用。")
                        .user(message)
                        .advisors(a -> a.param("chat_memory_conversation_id", chatId)
                                .param("chat_memory_retrieval_size", 10))
                        .tools(ticketAgentTool, ticketBookingTool)
                        .stream()
                        .content();
                break;
        }

        // 2. 将清理动作挂载到异步流的终点！
        Flux<String> finalStream = responseStream.doFinally(signalType -> {
            AgentRequestContext.clear();
        });
        // 【高光时刻】：强行注入反缓冲 Header，击穿各路网关的缓存池！
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache") // 浏览器别缓存
                .header("Connection", "keep-alive")
                .header("X-Accel-Buffering", "no")   // Nginx 别缓存
                .body(finalStream);
    }
}