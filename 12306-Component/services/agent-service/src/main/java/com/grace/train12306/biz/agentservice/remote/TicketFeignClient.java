package com.grace.train12306.biz.agentservice.remote;

import com.grace.train12306.framework.starter.convention.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 远程调用车票微服务
 */
@FeignClient(value = "train12306-ticket-service")
public interface TicketFeignClient {
    /**
     * 对应 ticket-service 的查票接口
     */
    @GetMapping("/api/ticket-service/ticket/query")
    Result<Object> queryTickets(@RequestParam("fromStation") String fromStation,
                        @RequestParam("toStation") String toStation,
                        @RequestParam("departureDate") String departureDate);
    /**
     * 对应 ticket-service 的全量车站接口
     */
    @GetMapping("/api/ticket-service/station/all")
    Result<List<Map<String, Object>>>  listAllStations();
}
