package com.grace.train12306.biz.agentservice.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.grace.train12306.biz.agentservice.remote.TicketFeignClient;
import com.grace.train12306.framework.starter.convention.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketBookingTool {

    private final TicketFeignClient ticketFeignClient;

    @Tool(description = "预订/购买火车票。当用户明确表达购票意图，且提供了车次(如D717)、乘车人、席别(如二等座)时调用。")
    public String bookTicket(String trainNumber, String passengerName, String seatType) {
        log.info("🚀 [Agent 动作] 正在尝试下单。车次: {}, 乘客: {}, 席别: {}", trainNumber, passengerName, seatType);

        try {
            // 1. 组装 12306 后端下单微服务所需的参数 (根据你本地项目的真实接口字段对齐)
            Map<String, Object> orderParam = new HashMap<>();
            orderParam.put("trainNumber", trainNumber);
            orderParam.put("passengerName", passengerName);
            orderParam.put("seatType", seatType);

            // 2. 发起分布式 RPC 调用
            Result<Object> result = ticketFeignClient.purchaseTicket(orderParam);

            // 3. 解析并回传给大模型
            if (result.isSuccess() && result.getData() != null) {
                JSONObject orderData = JSON.parseObject(JSON.toJSONString(result.getData()));
                String orderSn = orderData.getString("orderSn"); // 假设返回订单号

                JSONObject successResponse = new JSONObject();
                successResponse.put("status", "SUCCESS");
                successResponse.put("orderSn", orderSn);
                successResponse.put("msg", "下单成功，系统已为您成功锁定席位！");
                return successResponse.toJSONString();
            }

            return "下单失败，原因：" + result.getMessage();
        } catch (Exception e) {
            log.error("Agent 购票 RPC 异常", e);
            return "购票通道拥挤，分布式扣减库存失败，请稍后再试。";
        }
    }
}