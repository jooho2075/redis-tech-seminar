package dev.woorifis.controller;

import dev.woorifis.repository.WaitingUserRepository;
import dev.woorifis.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final WaitingUserRepository waitingUserRepository;

    /**
     * [Redis 모드] 대기열 진입
     */
    @PostMapping("/join")
    public String join(@RequestParam String userId) {
        ticketService.enterQueue(userId, "REDIS");
        return "Redis 대기열 등록 성공";
    }

    /**
     * [RDBMS 모드] 대기열 진입
     */
    @PostMapping("/join-rdbms")
    public String joinRdbms(@RequestParam String userId) {
        ticketService.enterQueue(userId, "RDBMS");
        return "RDBMS 적재 시작됨";
    }

    /**
     * 상태 확인 (내 앞에 몇 명?)
     * rank가 null이거나 0 이하이면 '진입 허용' 상태를 반환합니다.
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus(@RequestParam String userId, @RequestParam(defaultValue = "REDIS") String mode) {
        Long rank = ticketService.getWaitCount(userId, mode);
        Map<String, Object> response = new HashMap<>();

        // rank가 null(삭제 완료)이거나 0보다 작으면(이미 내 순서가 지남) 진입 허용
        if (rank == null || rank < 0) {
            response.put("status", "allowed");
            response.put("rank", 0);
        } else {
            response.put("status", "waiting");
            // 화면 표시용 (0등이면 '1명 대기'로 표시)
            response.put("rank", rank + 1);
        }
        return response;
    }

    /**
     * 결과 조회 API
     * Redis와 RDBMS의 시작/종료 타임스탬프를 반환합니다.
     */
    @GetMapping("/result")
    public Map<String, Object> getResult() {
        return ticketService.getResultTimestamps();
    }

    /**
     * 리셋 API
     */
    @PostMapping("/reset")
    public String reset() {
        ticketService.reset();
        return "초기화 완료";
    }
}