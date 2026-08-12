package com.itee.orchestrator.ingest;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.itee.orchestrator.domain.ExceptionStatus;
import com.itee.orchestrator.domain.TradeException;

public interface TradeExceptionRepository extends JpaRepository<TradeException, UUID> {

    List<TradeException> findAllByOrderByCreatedAtDesc();

    List<TradeException> findByStatusOrderByCreatedAtDesc(ExceptionStatus status);

    List<TradeException> findByStatusInOrderByCreatedAtAsc(Collection<ExceptionStatus> statuses);
}
