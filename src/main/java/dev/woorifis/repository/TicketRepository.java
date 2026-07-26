package dev.woorifis.repository;

import dev.woorifis.domain.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// RDBMS에 접근하기 위한 인터페이스
@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

}
