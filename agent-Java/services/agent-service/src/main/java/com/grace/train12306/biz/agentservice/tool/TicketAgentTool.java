package com.grace.train12306.biz.agentservice.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.grace.train12306.biz.agentservice.config.StationDictionaryLoader;
import com.grace.train12306.biz.agentservice.remote.TicketFeignClient;
import com.grace.train12306.framework.starter.convention.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketAgentTool {

    private final TicketFeignClient ticketFeignClient;

    //定义强类型的 JSON Schema 约束载体
    public record QueryTicketReq(
            @JsonProperty(required = true)
            @JsonPropertyDescription("出发地，必须是明确的中文站名（绝对不能为null），如：北京南")
            String fromStation,

            @JsonProperty(required = true)
            @JsonPropertyDescription("目的地，必须是明确的中文站名（绝对不能为null），如：杭州东")
            String toStation,

            @JsonProperty(required = true)
            @JsonPropertyDescription("出发日期，格式必须为 yyyy-MM-dd")
            String departureDate
    ) {}

    @Tool(description = "查询火车票。用于获取车次列表以及后台下单所需的列车标识 trainId。")
public String queryTicket(QueryTicketReq req) {
    // 大模型框架会自动将符合要求的 JSON 映射为这个 Record 对象
    String fromStation = req.fromStation();
    String toStation = req.toStation();
    String departureDate = req.departureDate();

    log.info("🎫 [queryTicket] 收到强类型校验请求: fromStation={}, toStation={}, departureDate={}", fromStation, toStation, departureDate);

    // 转换站名到站编码 (兼容联想与兜底)
    String fromCode = StationDictionaryLoader.STATION_MAP.getOrDefault(fromStation, fromStation);
    String toCode = StationDictionaryLoader.STATION_MAP.getOrDefault(toStation, toStation);
    log.info("🎫 [queryTicket] 编码转换: {} -> {}, {} -> {}", fromStation, fromCode, toStation, toCode);

    try {
        // 发起分布式 RPC 调用
        Result<Object> result = ticketFeignClient.queryTickets(fromCode, toCode, departureDate);
        log.info("🎫 [queryTicket] API返回: success={}", result.isSuccess());

        if (result.isSuccess() && result.getData() != null) {
            JSONObject raw = JSON.parseObject(JSON.toJSONString(result.getData()));
            JSONArray trains = raw.getJSONArray("trainList");

            if (trains == null || trains.isEmpty()) {
                log.info("🎫 [queryTicket] 未找到车次");
                return "未找到" + departureDate + "从" + fromStation + "到" + toStation + "的列车。请提醒用户更换日期或调整站点。";
            }

            log.info("🎫 [queryTicket] 找到 {} 个车次", trains.size());

            // 返回简洁文本并压缩上下文
            StringBuilder sb = new StringBuilder();
            sb.append("查询成功！找到 ").append(trains.size()).append(" 个车次：\n");

            int showCount = Math.min(trains.size(), 5);
            for (int i = 0; i < showCount; i++) {
                JSONObject t = trains.getJSONObject(i);
                String trainNumber = t.getString("trainNumber");
                String trainId = t.getString("trainId");
                String depName = t.getString("departure");
                String arrName = t.getString("arrival");
                String depTime = t.getString("departureTime");
                String arrTime = t.getString("arrivalTime");

                sb.append(trainNumber).append("次 ").append(depName).append("(").append(depTime).append(")→").append(arrName).append("(").append(arrTime).append(")");

                // 把零散的心理暗示升级为“强约束参数包”
                sb.append(" 【🚨下单调用 TicketBookingTool 时，必须严格使用以下三个精确参数：")
                        .append("trainId=").append(trainId).append(", ")
                        .append("departure=").append(depName).append(", ")
                        .append("arrival=").append(arrName)
                        .append("】\n");
            }

            String responseStr = sb.toString();
            log.info("🎫 [queryTicket] 返回给大模型的结果:\n{}", responseStr);
            return responseStr;
        }

        log.warn("⚠️ [queryTicket] API返回失败: {}", result.getMessage());
        return "查询失败：" + (result.getMessage() != null ? result.getMessage() : "请稍后重试");
    } catch (Exception e) {
        log.error("❌ [queryTicket] 异常", e);
        return "底层微服务查询出错，请稍后再试。";
    }
}
}