package io.github.salyvn.omnipet.core.management;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.UnaryOperator;

import io.github.salyvn.omnipet.core.domain.PetInstance;
import io.github.salyvn.omnipet.core.domain.PlayerState;
import io.github.salyvn.omnipet.core.persistence.PlayerStateRepository;

/** Persists stable-ID management mutations without replacing unrelated pet metadata. */
public final class RepositoryPetManagementService {
    private final PlayerStateRepository players;

    public RepositoryPetManagementService(PlayerStateRepository players) {
        this.players = Objects.requireNonNull(players, "player state repository");
    }

    public PetManagementResult favorite(UUID playerId, long revision, UUID petId, boolean favorite)
            throws IOException {
        return update(playerId, revision, petId,
                metadata -> metadata.withFavorite(favorite), "favorite updated");
    }

    public PetManagementResult lock(UUID playerId, long revision, UUID petId, boolean locked)
            throws IOException {
        return update(playerId, revision, petId,
                metadata -> metadata.withLocked(locked), "lock updated");
    }

    public PetManagementResult rename(UUID playerId, long revision, UUID petId, String customName)
            throws IOException {
        return update(playerId, revision, petId,
                metadata -> metadata.withCustomName(customName), "custom name updated");
    }

    public PetManagementResult move(UUID playerId, long revision, UUID petId, int targetIndex)
            throws IOException {
        requireIds(playerId, petId);
        try {
            final PetManagementResult[] captured = new PetManagementResult[1];
            PlayerState saved = players.withLocked(playerId, revision, current -> {
                int sourceIndex = petIndex(current.pets(), petId);
                if (sourceIndex < 0) throw rejected(PetManagementResult.Status.PET_NOT_FOUND, current, null,
                        "pet UUID is not owned");
                if (targetIndex < 0 || targetIndex >= current.pets().size()) {
                    throw rejected(PetManagementResult.Status.INVALID_MOVE, current, current.pets().get(sourceIndex),
                            "target position is outside the vault");
                }
                if (sourceIndex == targetIndex) {
                    throw rejected(PetManagementResult.Status.REJECTED, current, current.pets().get(sourceIndex),
                            "pet is already at that position");
                }
                List<PetInstance> pets = new ArrayList<>(current.pets());
                PetInstance pet = pets.remove(sourceIndex);
                pets.add(targetIndex, pet);
                captured[0] = result(PetManagementResult.Status.PERSISTED, current, pet, "pet moved");
                return withPets(current, pets);
            });
            return result(PetManagementResult.Status.PERSISTED, saved,
                    saved.pets().stream().filter(pet -> pet.id().equals(petId)).findFirst().orElseThrow(), "pet moved");
        } catch (Rejected rejected) {
            return rejected.result;
        }
    }

    private PetManagementResult update(
            UUID playerId,
            long revision,
            UUID petId,
            UnaryOperator<PetManagementMetadata> mutation,
            String detail) throws IOException {
        requireIds(playerId, petId);
        try {
            PlayerState saved = players.withLocked(playerId, revision, current -> {
                int index = petIndex(current.pets(), petId);
                if (index < 0) throw rejected(PetManagementResult.Status.PET_NOT_FOUND, current, null,
                        "pet UUID is not owned");
                PetInstance existing = current.pets().get(index);
                PetManagementMetadata before = PetManagementMetadata.read(existing);
                PetInstance updated = PetManagementMetadata.write(existing, mutation.apply(before));
                if (updated.equals(existing)) {
                    throw rejected(PetManagementResult.Status.REJECTED, current, existing, "management state is unchanged");
                }
                List<PetInstance> pets = new ArrayList<>(current.pets());
                pets.set(index, updated);
                return withPets(current, pets);
            });
            PetInstance pet = saved.pets().stream().filter(candidate -> candidate.id().equals(petId)).findFirst().orElseThrow();
            return result(PetManagementResult.Status.PERSISTED, saved, pet, detail);
        } catch (Rejected rejected) {
            return rejected.result;
        }
    }

    private static PlayerState withPets(PlayerState state, List<PetInstance> pets) {
        return state.withStorage(pets, state.vaultCapacity(), state.activeSlotCount(),
                state.desiredActivePetIds(), state.slotEntitlements());
    }

    private static int petIndex(List<PetInstance> pets, UUID petId) {
        for (int index = 0; index < pets.size(); index++) if (pets.get(index).id().equals(petId)) return index;
        return -1;
    }

    private static void requireIds(UUID playerId, UUID petId) {
        Objects.requireNonNull(playerId, "management player ID");
        Objects.requireNonNull(petId, "management pet ID");
    }

    private static Rejected rejected(
            PetManagementResult.Status status, PlayerState state, PetInstance pet, String detail) {
        return new Rejected(result(status, state, pet, detail));
    }

    private static PetManagementResult result(
            PetManagementResult.Status status, PlayerState state, PetInstance pet, String detail) {
        return new PetManagementResult(status, state, pet, detail);
    }

    private static final class Rejected extends RuntimeException {
        private final PetManagementResult result;

        private Rejected(PetManagementResult result) {
            super(null, null, false, false);
            this.result = result;
        }
    }
}
