package com.grace.train12306.biz.agentservice.config;

import com.grace.train12306.biz.agentservice.remote.TicketFeignClient;
import com.grace.train12306.framework.starter.convention.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class StationDictionaryLoader implements ApplicationRunner {

    // 内存字典供 Tool 调用：站名 -> 站编码
    public static final Map<String, String> STATION_MAP = new ConcurrentHashMap<>();
    // 站名 -> 区域编码（用于前端跳转）
    public static final Map<String, String> REGION_MAP = new ConcurrentHashMap<>();
    private final TicketFeignClient ticketFeignClient;

    @Override
    public void run(ApplicationArguments args) {
        log.info("🚀 [Agent] 开始同步远程车站字典...");
        try {
            Result<List<Map<String, Object>>> result = ticketFeignClient.listAllStations();
            if (result.isSuccess() && result.getData() != null) {
                result.getData().forEach(station -> {
                    String name = String.valueOf(station.get("name")); // 例如："北京南"
                    String code = String.valueOf(station.get("code"));
                    String region = String.valueOf(station.get("region"));

                    // 1. 存入标准站名
                    STATION_MAP.put(name, code);
                    REGION_MAP.put(name, region);

                    // 👉 2. 核心容错：给所有的站名强制加一个“站”字尾缀作为别名，一并存入字典！
                    // 这样不管大模型传过来的是“北京南”还是“北京南站”，都能瞬间秒速命中！
                    if (!name.endsWith("站")) {
                        String aliasName = name + "站";
                        STATION_MAP.put(aliasName, code);
                        REGION_MAP.put(aliasName, region);
                    }
                });
                log.info("✅ [Agent] 字典同步成功，已缓存 {} 个车站", STATION_MAP.size());
            }
        } catch (Exception e) {
            log.error("❌ 字典同步失败，请检查 ticket-service 是否在线", e);
        }
    }
}