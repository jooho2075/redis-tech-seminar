package dev.woorifis.repository;

import dev.woorifis.entity.WaitingUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WaitingUserRepository extends JpaRepository<WaitingUser, Long> {
    WaitingUser findByUserId(String userId);

    long countByEnterTimeBefore(Long time);

    // RDBMS 성능 한계를 보여주기 위한 대량 삭제 쿼리
    @Modifying
    @Query(value = "DELETE FROM waiting_users WHERE id IN (SELECT id FROM waiting_users ORDER BY enter_time ASC LIMIT :limit)", nativeQuery = true)
    void deleteTopN(@Param("limit") int limit);
}