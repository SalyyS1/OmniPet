package io.github.salyvn.omnipet.paper.buff;

import java.util.ArrayList;
import java.util.List;

import io.github.salyvn.omnipet.core.buff.PetStatBuff;

/**
 * Why a player's pet stats are or are not reaching them, in words an operator can act on.
 *
 * <p>Exists because every layer of this path reported success while the player's numbers never moved. The
 * projection read its node, the coordinator fired on activation, the adapter installed a modifier — and the
 * stat it installed onto was one MythicLib had never registered, so nothing read it. Three rounds of fixes
 * went into that path on reasoning alone, because there was no way to ask the running server what it
 * actually saw.
 *
 * <p>Pure and Bukkit-free: it takes what was found and renders the explanation, so the wording is testable
 * without a server. The command supplies the facts.
 */
public final class OwnerBuffDiagnostics {
    private OwnerBuffDiagnostics() {}

    /**
     * The report for one owner.
     *
     * @param providerPresent whether MythicLib is installed and enabled
     * @param providerDetail why the adapter is unusable, or null when it is fine
     * @param activePets how many pets the owner currently has out
     * @param buffs the stat buffs those pets project, after ID translation
     * @param registeredStats the stat IDs MythicLib actually knows, or null when it could not be asked
     */
    public static List<String> describe(
            boolean providerPresent,
            String providerDetail,
            int activePets,
            List<PetStatBuff> buffs,
            java.util.Set<String> registeredStats) {
        List<PetStatBuff> stats = List.copyOf(buffs == null ? List.of() : buffs);
        List<String> lines = new ArrayList<>();

        if (!providerPresent) {
            lines.add("MythicLib is not installed or not enabled, so no pet stat can be applied.");
            lines.add("Everything else about the pet still works; only the stat numbers are inert.");
            return List.copyOf(lines);
        }
        if (providerDetail != null && !providerDetail.isBlank()) {
            lines.add("The MythicLib adapter is not usable: " + providerDetail);
            return List.copyOf(lines);
        }

        if (activePets == 0) {
            lines.add("No pet is out, so there is nothing to apply. Summon one and run this again.");
            return List.copyOf(lines);
        }
        if (stats.isEmpty()) {
            lines.add("Those " + activePets + " active pet(s) grant no stats at all. Either their definition");
            lines.add("has no stats: block, or they predate stat rolling — hatch a fresh one to compare.");
            return List.copyOf(lines);
        }

        List<String> unknown = new ArrayList<>();
        for (PetStatBuff buff : stats) {
            if (registeredStats != null && !registeredStats.contains(buff.statId())) unknown.add(buff.statId());
        }

        // The healthy answer is one line. An operator running this on a working server is confirming a
        // suspicion, not auditing: listing every stat buries that answer, and the numbers are already
        // visible on their character sheet. Only the broken cases earn detail, because only they need it.
        if (registeredStats == null) {
            lines.add(activePets + " pet(s), " + stats.size() + " stat(s) sent to MythicLib.");
            lines.add("Its stat registry could not be read, so those IDs are unverified.");
            return List.copyOf(lines);
        }
        if (unknown.isEmpty()) {
            lines.add("OK — " + activePets + " pet(s), " + stats.size()
                    + " stat(s), all registered with MythicLib and being applied.");
            lines.add("A number still wrong in game means something else is reading it: check");
            lines.add("MythicLib's own configuration for that stat. Add 'all' to list them.");
            return List.copyOf(lines);
        }

        lines.add("BROKEN — MythicLib does not know " + unknown.size() + " of " + stats.size()
                + " stat(s), so they do nothing:");
        unknown.forEach(id -> lines.add("  " + id));
        lines.add("Fix the stat ID in the pet definition. MythicLib knows these:");
        lines.add("  " + preview(registeredStats));
        return List.copyOf(lines);
    }

    /**
     * The same report with every stat listed, for {@code /pet admin stats <player> all}.
     *
     * <p>Kept as a separate entry point rather than as a flag threaded through the summary, so the default
     * cannot drift back into the wall of text it was. An operator who wants the full list asks for it.
     */
    public static List<String> describeVerbose(
            boolean providerPresent,
            String providerDetail,
            int activePets,
            List<PetStatBuff> buffs,
            java.util.Set<String> registeredStats) {
        List<String> summary = describe(providerPresent, providerDetail, activePets, buffs, registeredStats);
        List<PetStatBuff> stats = List.copyOf(buffs == null ? List.of() : buffs);
        if (stats.isEmpty()) return summary;

        List<String> lines = new ArrayList<>(summary);
        lines.add("Every stat, as it will be sent to MythicLib:");
        for (PetStatBuff buff : stats) {
            boolean known = registeredStats == null || registeredStats.contains(buff.statId());
            lines.add("  " + (known ? "ok  " : "BAD ") + buff.statId()
                    + "  " + buff.modifierType() + "  " + buff.value());
        }
        return List.copyOf(lines);
    }

    /**
     * A short, alphabetised sample of the registered stat names.
     *
     * <p>Bounded because a server with an item plugin installed can register well over a hundred stats, and
     * a wall of them in chat buries the line that matters.
     */
    private static String preview(java.util.Set<String> registeredStats) {
        List<String> sorted = new ArrayList<>(registeredStats);
        sorted.sort(String::compareTo);
        int shown = Math.min(12, sorted.size());
        String sample = String.join(", ", sorted.subList(0, shown));
        return sorted.size() <= shown ? sample : sample + ", … (" + sorted.size() + " total)";
    }
}
