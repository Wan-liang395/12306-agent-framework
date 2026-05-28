package com.grace.train12306.biz.agentservice.controller;

import com.grace.train12306.biz.agentservice.rag.RailwayQaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
public class RagController {
    private final RailwayQaService railwayQaService;

    /**
     * 铁路知识库流式问答接口
     * 必须指定 MediaType.TEXT_EVENT_STREAM_VALUE
     */
    @GetMapping(value = "/qa/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamQa(@RequestParam("question") String question) {
        return railwayQaService.streamAnswer(question);
    }
}
