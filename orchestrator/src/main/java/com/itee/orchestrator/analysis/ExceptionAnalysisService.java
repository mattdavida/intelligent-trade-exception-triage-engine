package com.itee.orchestrator.analysis;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.itee.orchestrator.ai.AiAnalyzeResponse;
import com.itee.orchestrator.ai.AiEngineClient;
import com.itee.orchestrator.confidence.ConfidenceResult;
import com.itee.orchestrator.confidence.ConfidenceScorer;
import com.itee.orchestrator.domain.ExceptionStatus;
import com.itee.orchestrator.domain.TradeException;
import com.itee.orchestrator.ingest.TradeExceptionRepository;
import com.itee.orchestrator.web.ExceptionEventHub;

@Service
public class ExceptionAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ExceptionAnalysisService.class);

    private final TradeExceptionRepository repository;
    private final AiEngineClient aiEngineClient;
    private final ConfidenceScorer confidenceScorer;
    private final ExceptionEventHub eventHub;
    private final TransactionTemplate transactionTemplate;

    public ExceptionAnalysisService(
            TradeExceptionRepository repository,
            AiEngineClient aiEngineClient,
            ConfidenceScorer confidenceScorer,
            ExceptionEventHub eventHub,
            TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.aiEngineClient = aiEngineClient;
        this.confidenceScorer = confidenceScorer;
        this.eventHub = eventHub;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Runs after Kafka ingest commit. HTTP call is intentionally outside any DB transaction.
     */
    public void analyzeAsync(UUID exceptionId) {
        TradeException current = transactionTemplate.execute(status -> markAnalyzing(exceptionId));
        if (current == null) {
            return;
        }
        eventHub.publish(current);

        try {
            // No DB transaction held across this HTTP call
            AiAnalyzeResponse ai = aiEngineClient.analyze(current);
            ConfidenceResult confidence = confidenceScorer.score(current);
            TradeException done =
                    transactionTemplate.execute(status -> completeSuccess(exceptionId, ai, confidence));
            eventHub.publish(done);
            log.info(
                    "Analysis complete id={} tradeId={} severity={} confidence={}",
                    done.getId(),
                    done.getTradeId(),
                    done.getSeverity(),
                    done.getConfidenceScore());
        } catch (Exception e) {
            log.error("Analysis failed id={}: {}", exceptionId, e.getMessage());
            TradeException failed = transactionTemplate.execute(status -> markFailed(exceptionId));
            if (failed != null) {
                eventHub.publish(failed);
            }
        }
    }

    private TradeException markAnalyzing(UUID id) {
        return repository
                .findById(id)
                .map(ex -> {
                    if (ex.getStatus() != ExceptionStatus.NEW
                            && ex.getStatus() != ExceptionStatus.ANALYZING_FAILED) {
                        log.warn("Skip analyze id={} status={}", id, ex.getStatus());
                        return null;
                    }
                    ex.setStatus(ExceptionStatus.ANALYZING);
                    return repository.save(ex);
                })
                .orElse(null);
    }

    private TradeException completeSuccess(UUID id, AiAnalyzeResponse ai, ConfidenceResult confidence) {
        TradeException ex = repository.findById(id).orElseThrow();
        ex.setSeverity(ai.getSeverity());
        ex.setRecommendation(ai.getRecommendation());
        ex.setReasoning(ai.getReasoning());
        ex.setConfidenceScore(confidence.score());
        ex.setConfidenceRubricVersion(confidence.rubricVersion());
        ex.setConfidenceFactors(confidence.factors());
        ex.setStatus(ExceptionStatus.PENDING_REVIEW);
        return repository.save(ex);
    }

    private TradeException markFailed(UUID id) {
        return repository
                .findById(id)
                .map(ex -> {
                    ex.setStatus(ExceptionStatus.ANALYZING_FAILED);
                    return repository.save(ex);
                })
                .orElse(null);
    }
}
