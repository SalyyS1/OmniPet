package io.github.salyvn.omnipet.paper.management;

import java.util.UUID;

@FunctionalInterface
public interface PetManagementAuthorizationPort {
    Decision authorize(UUID viewerId, UUID ownerId, UUID petId, PetManagementOperation operation);

    static PetManagementAuthorizationPort ownerOnly() {
        return (viewerId, ownerId, petId, operation) -> viewerId.equals(ownerId)
                ? Decision.permit()
                : Decision.denied("cross-player pet management is not authorized");
    }

    record Decision(boolean allowed, String detail) {
        public Decision {
            detail = detail == null ? "" : detail;
        }

        public static Decision permit() { return new Decision(true, ""); }

        public static Decision denied(String detail) { return new Decision(false, detail); }
    }
}
