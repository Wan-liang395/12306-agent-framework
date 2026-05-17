package com.grace.train12306.biz.agentservice.remote;

import com.grace.train12306.framework.starter.convention.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 远程调用用户微服务
 */
@FeignClient(value = "train12306-user-service")
public interface UserFeignClient {

    /**
     * 内部接口：根据用户名查询乘车人列表
     */
    @GetMapping("/api/user-service/inner/passenger/query")
    Result<List<Map<String, Object>>> queryPassengerByUsername(@RequestParam("username") String username);
}
