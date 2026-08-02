package io.github.salyvn.omnipet.paper.incubation.action;

import io.github.salyvn.omnipet.core.incubation.IncubationItemActionTransaction;

public record IncubationItemActionResult(Status status, IncubationItemActionTransaction transaction, String detail) {
    public enum Status {
        COMMITTED,
        ALREADY_COMMITTED,
        REFUNDED,
        PENDING,
        CAPACITY_REACHED,
        INVALID_TARGET,
        OPERATOR_REVIEW
    }
    public IncubationItemActionResult { detail = detail == null ? "" : detail; }
}
