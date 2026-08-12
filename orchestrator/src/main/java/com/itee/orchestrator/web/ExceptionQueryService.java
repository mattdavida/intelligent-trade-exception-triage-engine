package com.itee.orchestrator.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.itee.orchestrator.domain.ExceptionStatus;
import com.itee.orchestrator.domain.ResolveAction;
import com.itee.orchestrator.domain.TradeException;
import com.itee.orchestrator.ingest.TradeExceptionRepository;

@Service
public class ExceptionQueryService {

    private final TradeExceptionRepository repository;
    private final ExceptionEventHub eventHub;

    public ExceptionQueryService(TradeExceptionRepository repository, ExceptionEventHub eventHub) {
        this.repository = repository;
        this.eventHub = eventHub;
    }

    @Transactional(readOnly = true)
    public List<ExceptionResponse> list(ExceptionStatus status) {
        List<TradeException> rows =
                status == null ? repository.findAllByOrderByCreatedAtDesc() : repository.findByStatusOrderByCreatedAtDesc(status);
        return rows.stream().map(ExceptionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ExceptionResponse get(UUID id) {
        return ExceptionResponse.from(require(id));
    }

    @Transactional
    public ExceptionResponse resolve(UUID id, ResolveRequest request) {
        TradeException ex = require(id);
        ResolveAction action = request.getAction();

        // Approve AI only when analysis succeeded. Failed AI can still be Rejected/Overridden.
        if (action == ResolveAction.APPROVE) {
            if (ex.getStatus() != ExceptionStatus.PENDING_REVIEW || ex.getRecommendation() == null) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "APPROVE requires PENDING_REVIEW with an AI recommendation");
            }
        } else if (ex.getStatus() != ExceptionStatus.PENDING_REVIEW
                && ex.getStatus() != ExceptionStatus.ANALYZING_FAILED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Exception not resolvable from status " + ex.getStatus());
        }

        if (action == ResolveAction.OVERRIDE
                && (request.getOverrideRecommendation() == null
                        || request.getOverrideRecommendation().isBlank())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "overrideRecommendation required for OVERRIDE");
        }

        ExceptionStatus terminal =
                switch (action) {
                    case APPROVE -> ExceptionStatus.RESOLVED;
                    case REJECT -> ExceptionStatus.REJECTED;
                    case OVERRIDE -> ExceptionStatus.OVERRIDDEN;
                };

        ex.setResolveAction(action);
        ex.setResolveNotes(request.getNotes());
        ex.setOverrideRecommendation(request.getOverrideRecommendation());
        ex.setResolvedAt(Instant.now());
        ex.setStatus(terminal);
        TradeException saved = repository.save(ex);
        eventHub.publish(saved);
        return ExceptionResponse.from(saved);
    }

    private TradeException require(UUID id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exception not found"));
    }
}
