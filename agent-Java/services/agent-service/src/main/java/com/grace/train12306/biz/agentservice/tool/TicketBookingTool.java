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

    // 席别名称 → 编码映射
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

        // 1：参数格式异常，打回让大模型重做
        if (trainId == null || !trainId.matches("^\\d+$")) {
            log.warn("⚠️ [Agent 拦截] 大模型传错 trainId：{}", trainId);
            // 这里返回的不是给用户的报错，而是给大模型的指令！
            return "执行失败：你传入的 trainId (" + trainId + ") 格式不正确，必须是纯数字！请反思并在后台自动提取正确的数字重新调用本工具，不要把这个错误直接告诉用户。";
        }

        log.info("🚀 [Agent 动作] 正在下单。trainId: {}, {}→{}, 乘客: {}, 席别: {}", trainId, departure, arrival, passengerName, seatType);

        try {
            Integer seatTypeCode = SEAT_TYPE_MAP.get(seatType);
            if (seatTypeCode == null) {
                return "执行失败：不支持的席别类型 " + seatType + "。请告诉用户系统仅支持：" + SEAT_TYPE_MAP.keySet() + "，并询问其是否更换席别。";
            }

            String username = AgentRequestContext.getUsername();
            if (username == null || username.isEmpty()) {
                return "执行失败：无法获取当前用户信息。请礼貌地提示用户：‘请先在前端完成登录，再进行购票。’";
            }

            Result<List<Map<String, Object>>> passengerResult = userFeignClient.queryPassengerByUsername(username);
            if (!passengerResult.isSuccess() || passengerResult.getData() == null) {
                return "执行失败：乘车人列表查询异常。请提示用户检查账号状态。";
            }

            String passengerId = null;
            for (Map<String, Object> p : passengerResult.getData()) {
                String realName = (String) p.getOrDefault("realName", p.get("real_name"));
                String id = (String) p.getOrDefault("id", p.get("ID"));
                if (passengerName.equals(realName)) {
                    passengerId = id;
                    break;
                }
            }
            if (passengerId == null) {
                return "执行失败：未找到名为「" + passengerName + "」的乘车人。请主动提示用户：‘系统中没有找到该联系人，请先在 12306 常用联系人中添加后再试。’";
            }

            Map<String, Object> orderParam = new HashMap<>();
            orderParam.put("trainId", trainId);
            orderParam.put("departure", departure);
            orderParam.put("arrival", arrival);

            Map<String, Object> passenger = new HashMap<>();
            passenger.put("passengerId", passengerId);
            passenger.put("seatType", seatTypeCode);
            orderParam.put("passengers", List.of(passenger));

            // 发起分布式事务调用
            Result<Object> result = ticketFeignClient.purchaseTicket(orderParam);

            if (result.isSuccess() && result.getData() != null) {
                JSONObject orderData = JSON.parseObject(JSON.toJSONString(result.getData()));
                String orderSn = orderData.getString("orderSn");

                JSONObject successResponse = new JSONObject();
                successResponse.put("status", "SUCCESS");
                successResponse.put("orderSn", orderSn);
                successResponse.put("msg", "下单成功，系统已为您成功锁定席位！");
                return successResponse.toJSONString();
            }

            // 2：业务被拒绝（如余票不足、防重放拦截）
            log.warn("⚠️ [Agent 观察] 微服务拒绝下单，原因：{}", result.getMessage());
            return "执行下单失败，底层系统提示：" + result.getMessage() + "。请基于此原因向用户解释，并主动提供替代建议（例如：如果余票不足，请主动推荐用户购买一等座或其他车次）。";

        } catch (Exception e) {
            // 3：严重网络异常或服务熔断降级
            log.error("❌ Agent 购票 RPC 异常", e);
            return "执行下单时底层微服务发生网络拥挤或超时。请停止调用工具，用安抚的语气告诉用户系统当前正在排队或维护中，建议稍后再试。";
        }
    }
}