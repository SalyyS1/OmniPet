package io.github.salyvn.omnipet.paper.management;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import io.github.salyvn.omnipet.core.progression.CultivationItemActionService;
import io.github.salyvn.omnipet.core.progression.ProgressionMutationContext;
import io.github.salyvn.omnipet.core.release.ReleasePreview;

public final class PaperPetManagementController {
    private final PetManagementControllerContext context;
    private final PetManagementStateActions stateActions;
    private final PetManagementCultivationActions cultivationActions;
    private final PetManagementReleaseActions releaseActions;

    public PaperPetManagementController(
            PetManagementRepositoryPort repository,
            PetManagementAuthorizationPort authorization,
            CultivationItemActionService cultivationActions,
            PetConsumableInventoryPort consumables,
            PetManagementMainThread mainThread,
            PetReleaseOutboxRecoveryPort outboxRecovery,
            Executor repositoryExecutor) {
        this(repository, authorization, cultivationActions, consumables, mainThread,
                outboxRecovery, repositoryExecutor, UUID::randomUUID);
    }

    PaperPetManagementController(
            PetManagementRepositoryPort repository,
            PetManagementAuthorizationPort authorization,
            CultivationItemActionService cultivationActionService,
            PetConsumableInventoryPort consumables,
            PetManagementMainThread mainThread,
            PetReleaseOutboxRecoveryPort outboxRecovery,
            Executor repositoryExecutor,
            Supplier<UUID> ids) {
        context = new PetManagementControllerContext(
                repository, authorization, repositoryExecutor, Objects.requireNonNull(ids, "management UUID supplier"));
        stateActions = new PetManagementStateActions(context);
        cultivationActions = new PetManagementCultivationActions(
                context, cultivationActionService, consumables, mainThread);
        releaseActions = new PetManagementReleaseActions(context, outboxRecovery);
    }

    public CompletionStage<PetManagementOutcome> open(UUID viewerId, UUID ownerId, UUID petId) {
        return stateActions.open(viewerId, ownerId, petId);
    }

    public CompletionStage<PetManagementOutcome> favorite(PetManagementSession session, boolean value) {
        return stateActions.favorite(session, value);
    }

    public CompletionStage<PetManagementOutcome> lock(PetManagementSession session, boolean value) {
        return stateActions.lock(session, value);
    }

    public CompletionStage<PetManagementOutcome> move(PetManagementSession session, int targetIndex) {
        return stateActions.move(session, targetIndex);
    }

    public CompletionStage<PetManagementOutcome> addExperience(
            PetManagementSession session,
            ProgressionMutationContext progression) {
        return cultivationActions.addExperience(session, progression);
    }

    public CompletionStage<PetManagementOutcome> breakthrough(
            PetManagementSession session,
            ProgressionMutationContext progression) {
        return cultivationActions.breakthrough(session, progression);
    }

    public CompletionStage<PetManagementOutcome> recoverCultivation(
            PetManagementSession session,
            UUID actionToken,
            ProgressionMutationContext progression) {
        return cultivationActions.recover(session, actionToken, progression);
    }

    CompletionStage<PetManagementOutcome> recoverOwnedCultivation(
            UUID ownerId,
            UUID actionToken,
            ProgressionMutationContext progression) {
        return cultivationActions.recoverOwned(ownerId, actionToken, progression);
    }

    public CompletionStage<PetManagementOutcome> previewRelease(PetManagementSession session) {
        return releaseActions.preview(session);
    }

    public CompletionStage<PetManagementOutcome> confirmRelease(
            PetManagementSession session,
            ReleasePreview preview) {
        return releaseActions.confirm(session, preview);
    }

    public CompletionStage<PetManagementOutcome> recoverRelease(
            UUID viewerId,
            UUID ownerId,
            UUID petId,
            UUID transactionId) {
        return releaseActions.recover(viewerId, ownerId, petId, transactionId);
    }

    public void close(PetManagementSession session) {
        if (session != null) context.sessions.remove(session.viewerId(), session);
    }

    public void shutdown() {
        cultivationActions.shutdown();
        context.shutdown();
    }
}
