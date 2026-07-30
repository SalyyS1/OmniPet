package io.github.salyvn.omnipet.core.persistence;

import java.util.List;

public record MigrationReport(
        int sourceSchemaVersion,
        boolean migrated,
        List<String> assignedInstanceIds,
        List<String> warnings) {
    public MigrationReport {
        assignedInstanceIds = List.copyOf(assignedInstanceIds == null ? List.of() : assignedInstanceIds);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }
}
