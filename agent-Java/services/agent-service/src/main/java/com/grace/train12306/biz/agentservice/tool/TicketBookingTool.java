package com.grace.train12306.biz.agentservice.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.grace.train12306.biz.agentservice.context.AgentRequestContext;
import com.grace.train12306.biz.agentservice.remote.TicketFeignClient;
import com.grace.train12306.biz.agentservice.remote.UserFeignClient;
import com.grace.train12306.framework.starter.convention.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketBookingTool {

    private final TicketFeignClient ticketFeignClient;
    private final UserFeignClient userFeignClient;

    // 席别名称 → 编码映射 (完美对齐 12306 底层数据库)
    private static final Map<String, Integer> SEAT_TYPE_MAP = Map.ofEntries(
            Map.entry("商务座", 0),
            Map.entry("一等座", 1),
            Map.entry("二等座", 2),
            Map.entry("二等包座", 3),
            Map.entry("一等卧", 4),
            Map.entry("二等卧", 5),
            Map.entry("软卧", 6),
            Map.entry("硬卧", 7),
            Map.entry("软座", 8),
            Map.entry("硬座", 9),
            Map.entry("无座", 10)
    );

    // 👉 核心：定义强类型的入参 Schema 约束
    public record BookTicketReq(
            @JsonProperty(required = true)
            @JsonPropertyDescription("列车内部标识(绝对不能为null)，必须从查票结果的隐藏参数中提取")
            String trainId,

            @JsonProperty(required = true)
            @JsonPropertyDescription("出发站名，必须是明确的中文站名，如：北京南")
            String departure,

            @JsonProperty(required = true)
            @JsonPropertyDescription("到达站名，必须是明确的中文站名，如：杭州东")
            String arrival,

            @JsonProperty(required = true)
            @JsonPropertyDescription("乘车人真实姓名")
            String passengerName,

            @JsonProperty(required = true)
            @JsonPropertyDescription("席别，例如：二等座、一等座、商务座")
            String seatType
    ) {}

    @Tool(description = "预订/购买火车票。必须先调用 queryTicket 查票工具获取车次列表和对应的 trainId 后，才能调用此工具。")
    public String bookTicket(BookTicketReq req) {
        String trainId = req.trainId();
        String departure = req.departure();
        String arrival = req.arrival();
        String passengerName = req.passengerName();
        String seatType = req.seatType();
        // ==========================================
        // 🚨 新增：专门针对 AI 幻觉的防呆拦截
        // 如果大模型传进来的 trainId 长得像车次号（字母开头+数字，如 G35, D717）
        // ==========================================
        if (trainId == null || !trainId.matches("^\\d+$")) {
            log.warn("⚠️ [Agent 拦截] 大模型传错了 trainId，传了非法字符：{}", trainId);
            return "下单失败：系统错误！你传入的 trainId (" + trainId + ") 格式不正确，必须是纯数字！请严格从刚才的查票结果中，提取出 [重要!下单必须传此trainId: xxx] 里面的纯数字填入，绝对不能自己瞎编占位符！";
        }

        log.info("🚀 [Agent 动作] 正在下单。trainId: {}, {}→{}, 乘客: {}, 席别: {}", trainId, departure, arrival, passengerName, seatType);

        try {
            // 1. 解析席别编码
            Integer seatTypeCode = SEAT_TYPE_MAP.get(seatType);
            if (seatTypeCode == null) {
                return "不支持的席别类型：" + seatType + "。可选：" + SEAT_TYPE_MAP.keySet();
            }

            // 2. 隐式提取当前登录用户并验证
            String username = AgentRequestContext.getUsername();
            if (username == null || username.isEmpty()) {
                return "无法获取当前用户信息，请先在前端登录系统。";
            }

            // 3. 反查真实的乘车人ID
            Result<List<Map<String, Object>>> passengerResult = userFeignClient.queryPassengerByUsername(username);
            if (!passengerResult.isSuccess() || passengerResult.getData() == null) {
                return "查询乘车人信息失败，请确认该账号已添加常用联系人。";
            }

            String passengerId = null;
            for (Map<String, Object> p : passengerResult.getData()) {
                // 兼容 camelCase 和 snake_case
                String realName = (String) p.getOrDefault("realName", p.get("real_name"));
                String id = (String) p.getOrDefault("id", p.get("ID"));
                if (passengerName.equals(realName)) {
                    passengerId = id;
                    break;
                }
            }
            if (passengerId == null) {
                return "未找到乘车人「" + passengerName + "」，请确认已在系统中将该乘客添加为常用联系人。";
            }

            // 4. 严格组装微服务所需参数
            Map<String, Object> orderParam = new HashMap<>();
            orderParam.put("trainId", trainId);
            orderParam.put("departure", departure);
            orderParam.put("arrival", arrival);

            Map<String, Object> passenger = new HashMap<>();
            passenger.put("passengerId", passengerId);
            passenger.put("seatType", seatTypeCode);
            orderParam.put("passengers", List.of(passenger));

            // 5. 发起分布式事务调用
            Result<Object> result = ticketFeignClient.purchaseTicket(orderParam);

            if (result.isSuccess() && result.getData() != null) {
                JSONObject orderData = JSON.parseObject(JSON.toJSONString(result.getData()));
                String orderSn = orderData.getString("orderSn");

                // 👉 严格按照前端需要的格式返回，触发客票卡片
                JSONObject successResponse = new JSONObject();
                successResponse.put("status", "SUCCESS");
                successResponse.put("orderSn", orderSn);
                successResponse.put("msg", "下单成功，系统已为您成功锁定席位！");
                return successResponse.toJSONString();
            }

            return "微服务下单失败，原因：" + result.getMessage();
        } catch (Exception e) {
            log.error("Agent 购票 RPC 异常", e);
            return "购票通道拥挤或微服务网络异常，请稍后再试。";
        }
    }
}