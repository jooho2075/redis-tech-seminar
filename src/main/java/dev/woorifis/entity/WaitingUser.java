package dev.woorifis.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// RDBMS만 사용한 경우
@Entity
@Getter @Setter
@NoArgsConstructor
@Table(name = "waiting_users") // DB테이블 이름 지정
public class WaitingUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;
    private Long enterTime;

    public WaitingUser(String userId, Long enterTime) {
        this.userId = userId;
        this.enterTime = enterTime;
    }
}
