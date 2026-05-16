package com.grace.train12306.biz.agentservice.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
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

    @Tool(description = "查询火车票。需提供出发地、目的地和yyyy-MM-dd日期。")
    public String queryTicket(String fromStation, String toStation, String departureDate) {
        // 1. 联想：如果匹配不到，尝试加“南”等后缀联想
        String fromCode = StationDictionaryLoader.STATION_MAP.getOrDefault(fromStation, fromStation);
        String toCode = StationDictionaryLoader.STATION_MAP.getOrDefault(toStation, toStation);

        try {
            Result<Object> result = ticketFeignClient.queryTickets(fromCode, toCode, departureDate);
            if (result.isSuccess() && result.getData() != null) {
                JSONObject raw = JSON.parseObject(JSON.toJSONString(result.getData()));
                JSONArray trains = raw.getJSONArray("trainList");

                // 2. 压缩:只保留大模型关注的核心 4 个字段，展示前 10 趟
                // 防呆格式化：显式提供到达时间，并将历时转换为可读文本
                JSONArray slimList = new JSONArray();
                int showCount = Math.min(trains.size(), 10);
                for (int i = 0; i < showCount; i++) {
                    JSONObject t = trains.getJSONObject(i);
                    JSONObject s = new JSONObject();
                    s.put("车次", t.getString("trainNumber"));   // 车次
                    s.put("出发时间", t.getString("departureTime")); // 出发
                    s.put("到达时间", t.getString("arrivalTime")); // 到达
                    // 👉 核心修复：把 "06:25" 强制转换成 "6小时25分钟"，防止大模型误认
                    String rawDuration = t.getString("duration");
                    if (rawDuration != null && rawDuration.contains(":")) {
                        String[] parts = rawDuration.split(":");
                        try {
                            int hours = Integer.parseInt(parts[0]);
                            int minutes = Integer.parseInt(parts[1]);
                            s.put("历时", hours + "小时" + minutes + "分钟");
                        } catch (NumberFormatException e) {
                            s.put("历时", rawDuration);
                        }
                    } else {
                        s.put("历时", rawDuration);
                    }
                    s.put("余票信息", t.getJSONArray("seatClassList"));
                    slimList.add(s);
                }

                JSONObject aiResponse = new JSONObject();
                aiResponse.put("trains", slimList);
                aiResponse.put("total", trains.size());
                aiResponse.put("msg", "数据已精简，若班次过多请告知用户可查询具体时段。");
                return aiResponse.toJSONString();
            }
            return "未查到相关班次。";
        } catch (Exception e) {
            log.error("查票异常", e);
            return "服务响应慢，请稍后再试。";
        }
    }
}