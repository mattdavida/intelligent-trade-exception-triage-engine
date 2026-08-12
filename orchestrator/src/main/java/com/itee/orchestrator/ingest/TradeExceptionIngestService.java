package com.itee.orchestrator.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itee.orchestrator.analysis.ExceptionIngestedEvent;
import com.itee.orchestrator.domain.TradeException;
import com.itee.orchestrator.web.ExceptionEventHub;

@Service
public class TradeExceptionIngestService {

    private static final Logger log = LoggerFactory.getLogger(TradeExceptionIngestService.class);

    private final TradeExceptionMapper mapper;
    private final TradeExceptionRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final ExceptionEventHub eventHub;

    public TradeExceptionIngestService(
            TradeExceptionMapper mapper,
            TradeExceptionRepository repository,
            ApplicationEventPublisher eventPublisher,
            ExceptionEventHub eventHub) {
        this.mapper = mapper;
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.eventHub = eventHub;
    }

    /**
     * Persist NEW only and publish AFTER_COMMIT analyze event. Must not call AI here.
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
        // SSE after commit is ideal; publish best-effort now for NEW visibility
        eventHub.publish(saved);
        return saved;
    }
}
