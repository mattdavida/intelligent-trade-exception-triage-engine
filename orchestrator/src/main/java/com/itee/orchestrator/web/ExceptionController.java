package com.itee.orchestrator.web;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.itee.orchestrator.domain.ExceptionStatus;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/exceptions")
public class ExceptionController {

    private final ExceptionQueryService queryService;

    public ExceptionController(ExceptionQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public List<ExceptionResponse> list(@RequestParam(required = false) ExceptionStatus status) {
        return queryService.list(status);
    }

    @GetMapping("/{id}")
    public ExceptionResponse get(@PathVariable UUID id) {
        return queryService.get(id);
    }

    @PostMapping("/{id}/resolve")
    public ExceptionResponse resolve(@PathVariable UUID id, @Valid @RequestBody ResolveRequest request) {
        return queryService.resolve(id, request);
    }
}
