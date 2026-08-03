package io.github.salyvn.omnipet.paper.command;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Pure routing contract for the future Paper command adapter. */
public final class AdminPetCommandParser {
    public static final String GENERAL_PERMISSION = "omnipet.general";
    public static final String MANAGE_PET_PERMISSION = "omnipet.admin.managepet";
    private static final Set<String> ROOT_ALIASES = Set.of("pet", "pets");

    private AdminPetCommandParser() {}

    public static Result parse(Request request) {
        Objects.requireNonNull(request, "request");
        String alias = request.alias().toLowerCase(Locale.ROOT);
        if (!ROOT_ALIASES.contains(alias)) return new Rejected(RejectReason.UNKNOWN_ALIAS);
        if (!request.generalPermission()) return new Rejected(RejectReason.GENERAL_PERMISSION_REQUIRED);

        List<String> arguments = request.arguments();
        if (!arguments.isEmpty() && arguments.getFirst().equalsIgnoreCase("admin")) {
            return parseAdmin(request, arguments);
        }

        if (request.playerId() == null) return new Rejected(RejectReason.PLAYER_REQUIRED);
        // No arguments opens the hub. /pet <page> and /pet vault [page] keep direct vault access.
        if (arguments.isEmpty()) return new OpenHub(request.playerId());
        if (arguments.getFirst().equalsIgnoreCase("vault")) return parseVault(request, arguments);
        if (arguments.size() != 1) return new Rejected(RejectReason.INVALID_ARGUMENTS);
        try {
            int page = Integer.parseInt(arguments.getFirst());
            return page > 0
                    ? new PlayerPage(request.playerId(), page)
                    : new Rejected(RejectReason.INVALID_PAGE);
        } catch (NumberFormatException ignored) {
            return new Rejected(RejectReason.INVALID_ARGUMENTS);
        }
    }

    private static Result parseVault(Request request, List<String> arguments) {
        if (arguments.size() == 1) return new PlayerPage(request.playerId(), 1);
        if (arguments.size() != 2) return new Rejected(RejectReason.INVALID_ARGUMENTS);
        try {
            int page = Integer.parseInt(arguments.get(1));
            return page > 0
                    ? new PlayerPage(request.playerId(), page)
                    : new Rejected(RejectReason.INVALID_PAGE);
        } catch (NumberFormatException ignored) {
            return new Rejected(RejectReason.INVALID_ARGUMENTS);
        }
    }

    private static Result parseAdmin(Request request, List<String> arguments) {
        if (!request.managePetPermission()) return new Rejected(RejectReason.MANAGE_PET_PERMISSION_REQUIRED);
        if (arguments.size() != 2 || !arguments.get(1).equalsIgnoreCase("browse")) {
            return new Rejected(RejectReason.INVALID_ARGUMENTS);
        }
        if (request.playerId() == null) return new Rejected(RejectReason.PLAYER_REQUIRED);
        return new OpenAdminBrowse(request.playerId());
    }

    public record Request(
            String alias,
            List<String> arguments,
            UUID playerId,
            boolean generalPermission,
            boolean managePetPermission) {
        public Request {
            Objects.requireNonNull(alias, "alias");
            arguments = List.copyOf(arguments == null ? List.of() : arguments);
        }
    }

    public sealed interface Result permits OpenAdminBrowse, OpenHub, PlayerPage, Rejected {}

    public record OpenAdminBrowse(UUID viewerId) implements Result {
        public OpenAdminBrowse {
            Objects.requireNonNull(viewerId, "viewerId");
        }
    }

    /** {@code /pet} with no arguments: open the navigation hub. */
    public record OpenHub(UUID viewerId) implements Result {
        public OpenHub {
            Objects.requireNonNull(viewerId, "viewerId");
        }
    }

    public record PlayerPage(UUID viewerId, int page) implements Result {
        public PlayerPage {
            Objects.requireNonNull(viewerId, "viewerId");
            if (page < 1) throw new IllegalArgumentException("page must be one-based");
        }
    }

    public record Rejected(RejectReason reason) implements Result {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    public enum RejectReason {
        UNKNOWN_ALIAS,
        GENERAL_PERMISSION_REQUIRED,
        MANAGE_PET_PERMISSION_REQUIRED,
        PLAYER_REQUIRED,
        INVALID_PAGE,
        INVALID_ARGUMENTS
    }
}
