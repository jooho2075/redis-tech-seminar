package dev.woorifis.service;

import dev.woorifis.entity.WaitingUser;
import dev.woorifis.repository.WaitingUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    private final StringRedisTemplate redisTemplate;
    private final WaitingUserRepository waitingUserRepository;
    private final TransactionTemplate transactionTemplate;
    private static final String QUEUE_KEY = "ticket_queue";

    // 레이싱 상태 (감소 로직 활성화)
    private final AtomicBoolean isRedisRacing = new AtomicBoolean(false);
    private final AtomicBoolean isRdbmsRacing = new AtomicBoolean(false);

    // 적재 상태 (적재 중에는 rank 조회 시 0으로 조기 종료되는 것 방지)
    private final AtomicBoolean isRedisLoading = new AtomicBoolean(false);
    private final AtomicBoolean isRdbmsLoading = new AtomicBoolean(false);

    // 시간 기록용 변수 (밀리초 단위)
    private long redisStartTime, redisEndTime;
    private long rdbmsStartTime, rdbmsEndTime;

    @Async
    public void enterQueue(String userId, String mode) {
        long now = System.currentTimeMillis();
        int total = 50000;

        if ("REDIS".equals(mode)) {
            redisStartTime = System.currentTimeMillis(); // 시작 시간 기록
            redisEndTime = 0;
            isRedisLoading.set(true);
            isRedisRacing.set(false);
            log.info("Redis 적재 시작(5만명)");

            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                byte[] key = QUEUE_KEY.getBytes();
                for (int i = 1; i <= total; i++) {
                    connection.zAdd(key, (double) now + i, ("dummy_" + i).getBytes());
                }
                connection.zAdd(key, (double) now + total + 1, userId.getBytes());
                return null;
            });

            isRedisLoading.set(false);
            isRedisRacing.set(true);
            log.info("Redis 적재 완료! 티켓팅 시작");
        } else {
            rdbmsStartTime = System.currentTimeMillis(); // 시작 시간 기록
            rdbmsEndTime = 0;
            isRdbmsLoading.set(true);
            isRdbmsRacing.set(false);
            log.info("RDBMS 적재 시작(5만명)");

            transactionTemplate.execute(status -> {
                List<WaitingUser> dummies = new ArrayList<>();
                for (int i = 1; i <= total; i++) {
                    dummies.add(new WaitingUser("dummy_" + i, now + i));
                }
                dummies.add(new WaitingUser(userId, now + total + 1));

                waitingUserRepository.saveAll(dummies);
                waitingUserRepository.flush();
                return null;
            });

            isRdbmsLoading.set(false);
            isRdbmsRacing.set(true);
            log.info("RDBMS 적재 완료! 티켓팅 시작");
        }
    }

    @Scheduled(fixedDelay = 50)
    public void processRedisQueue() {
        if (!isRedisRacing.get()) return;

        int count = ThreadLocalRandom.current().nextInt(500, 1001);
        redisTemplate.opsForZSet().removeRange(QUEUE_KEY, 0, count - 1);

        Long size = redisTemplate.opsForZSet().size(QUEUE_KEY);
        if (size != null && size == 0) {
            isRedisRacing.set(false);
            redisEndTime = System.currentTimeMillis(); // 종료 시간 기록
            log.info("Redis 처리 종료");
        }
    }

    @Scheduled(fixedDelay = 50)
    public void processRdbmsQueue() {
        if (!isRdbmsRacing.get()) return;

        transactionTemplate.execute(status -> {
            int count = ThreadLocalRandom.current().nextInt(500, 1001);
            waitingUserRepository.deleteTopN(count);
            waitingUserRepository.flush();

            if (waitingUserRepository.count() == 0) {
                isRdbmsRacing.set(false);
                rdbmsEndTime = System.currentTimeMillis(); // 종료 시간 기록
                log.info("RDBMS 처리 종료");
            }
            return null;
        });
    }

    // 결과 조회를 위한 타임스탬프 반환 메서드
    public Map<String, Object> getResultTimestamps() {
        return Map.of(
                "redisStart", redisStartTime,
                "redisEnd", redisEndTime,
                "rdbmsStart", rdbmsStartTime,
                "rdbmsEnd", rdbmsEndTime
        );
    }

    public Long getWaitCount(String userId, String mode) {
        if ("REDIS".equals(mode)) {
            if (isRedisLoading.get()) return 50000L;
            // Redis의 Sorted Set이 줄을 다 세워놔서 내 순서만 추가됨
            return redisTemplate.opsForZSet().rank(QUEUE_KEY, userId);
        }

        if (isRdbmsLoading.get()) return 50000L;
        WaitingUser me = waitingUserRepository.findByUserId(userId);
        if (me == null) return null;
        // 나보다 먼저 온 사람이 몇 명인지 일일이 다 세어봐야 함
        return waitingUserRepository.countByEnterTimeBefore(me.getEnterTime());
    }

    public void reset() {
        isRedisRacing.set(false);
        isRdbmsRacing.set(false);
        isRedisLoading.set(false);
        isRdbmsLoading.set(false);
        redisStartTime = redisEndTime = rdbmsStartTime = rdbmsEndTime = 0; // 시간 초기화

        transactionTemplate.execute(status -> {
            redisTemplate.delete(QUEUE_KEY);
            waitingUserRepository.deleteAllInBatch();
            waitingUserRepository.flush();
            return null;
        });
        log.info("리셋 완료(DB 초기화 및 시간 리셋)");
    }
}