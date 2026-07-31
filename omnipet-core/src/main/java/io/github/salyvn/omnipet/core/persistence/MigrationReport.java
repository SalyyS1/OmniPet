package io.github.salyvn.omnipet.core.persistence;

import java.util.List;

public record MigrationReport(
        int sourceSchemaVersion,
        boolean migrated,
        List<String> assignedInstanceIds,
        List<String> appliedMigrations,
        List<String> warnings) {
    public MigrationReport {
        assignedInstanceIds = List.copyOf(assignedInstanceIds == null ? List.of() : assignedInstanceIds);
        appliedMigrations = List.copyOf(appliedMigrations == null ? List.of() : appliedMigrations);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
    }

    public MigrationReport(
            int sourceSchemaVersion,
            boolean migrated,
            List<String> assignedInstanceIds,
            List<String> warnings) {
        this(sourceSchemaVersion, migrated, assignedInstanceIds, List.of(), warnings);
    }
}
