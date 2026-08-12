package com.itee.orchestrator.web;

import com.itee.orchestrator.domain.ResolveAction;

import jakarta.validation.constraints.NotNull;

public class ResolveRequest {

    @NotNull
    private ResolveAction action;

    private String notes;
    private String overrideRecommendation;

    public ResolveAction getAction() {
        return action;
    }

    public void setAction(ResolveAction action) {
        this.action = action;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getOverrideRecommendation() {
        return overrideRecommendation;
    }

    public void setOverrideRecommendation(String overrideRecommendation) {
        this.overrideRecommendation = overrideRecommendation;
    }
}
