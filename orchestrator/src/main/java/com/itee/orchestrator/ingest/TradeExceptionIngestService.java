package com.itee.orchestrator.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itee.orchestrator.analysis.ExceptionIngestedEvent;
import com.itee.orchestrator.domain.TradeException;

@Service
public class TradeExceptionIngestService {

    private static final Logger log = LoggerFactory.getLogger(TradeExceptionIngestService.class);

    private final TradeExceptionMapper mapper;
    private final TradeExceptionRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public TradeExceptionIngestService(
            TradeExceptionMapper mapper,
            TradeExceptionRepository repository,
            ApplicationEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Persist NEW only and publish AFTER_COMMIT analyze/SSE event. Must not call AI here.
     */
    @Transactional
    public TradeException ingest(RawTradeExceptionEvent event) {
        TradeException entity = mapper.toEntity(event);
        TradeException saved = repository.save(entity);
        eventPublisher.publishEvent(new ExceptionIngestedEvent(saved.getId()));
        log.info(
                "Ingested exception id={} tradeId={} type={} status={}",
                saved.getId(),
                saved.getTradeId(),
                saved.getDiscrepancyType(),
                saved.getStatus());
        return saved;
    }
}
