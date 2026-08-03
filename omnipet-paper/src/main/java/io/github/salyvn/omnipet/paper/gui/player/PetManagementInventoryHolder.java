package io.github.salyvn.omnipet.paper.gui.player;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import io.github.salyvn.omnipet.core.release.ReleasePreview;
import io.github.salyvn.omnipet.paper.management.PetManagementSession;

public final class PetManagementInventoryHolder implements InventoryHolder {
    private final PetManagementSession session;
    private final UUID generationId;
    private final View view;
    private final Map<Integer, Action> actions;
    private final ReleasePreview releasePreview;
    private Inventory inventory;

    public PetManagementInventoryHolder(
            PetManagementSession session,
            View view,
            Map<Integer, Action> actions,
            ReleasePreview releasePreview) {
        this.session = Objects.requireNonNull(session, "management holder session");
        this.generationId = UUID.randomUUID();
        this.view = Objects.requireNonNull(view, "management holder view");
        this.actions = Map.copyOf(actions == null ? Map.of() : actions);
        this.releasePreview = releasePreview;
        if (view == View.RELEASE_CONFIRMATION && releasePreview == null) {
            throw new IllegalArgumentException("release confirmation requires a frozen preview");
        }
        if (releasePreview != null
                && (!releasePreview.playerId().equals(session.ownerId())
                || !releasePreview.petId().equals(session.petId())
                || releasePreview.expectedRevision() != session.expectedRevision())) {
            throw new IllegalArgumentException("release preview does not match management holder");
        }
    }

    public void bind(Inventory inventory) {
        if (this.inventory != null) throw new IllegalStateException("management inventory already bound");
        this.inventory = Objects.requireNonNull(inventory, "management inventory");
    }

    public PetManagementSession session() { return session; }
    public UUID viewerId() { return session.viewerId(); }
    public UUID ownerId() { return session.ownerId(); }
    public UUID petId() { return session.petId(); }
    public UUID sessionId() { return session.sessionId(); }
    public UUID generationId() { return generationId; }
    public long expectedRevision() { return session.expectedRevision(); }
    public View view() { return view; }
    public Action action(int rawSlot) { return actions.get(rawSlot); }
    public ReleasePreview releasePreview() { return releasePreview; }
    public boolean owns(Inventory candidate) { return inventory != null && inventory == candidate; }

    @Override public Inventory getInventory() { return inventory; }

    public record Action(Type type, boolean flag, int targetIndex) {
        public Action {
            Objects.requireNonNull(type, "management holder action type");
            if (type != Type.MOVE && targetIndex != -1) {
                throw new IllegalArgumentException("only move actions carry a target index");
            }
            if (type == Type.MOVE && targetIndex < 0) {
                throw new IllegalArgumentException("move target index cannot be negative");
            }
        }

        public static Action favorite(boolean value) { return new Action(Type.FAVORITE, value, -1); }
        public static Action lock(boolean value) { return new Action(Type.LOCK, value, -1); }
        public static Action move(int index) { return new Action(Type.MOVE, false, index); }
        public static Action simple(Type type) { return new Action(type, false, -1); }
    }

    public enum View { MANAGEMENT, RELEASE_CONFIRMATION }

    public enum Type {
        FAVORITE,
        LOCK,
        MOVE,
        ADD_EXPERIENCE,
        BREAKTHROUGH,
        PREVIEW_RELEASE,
        CONFIRM_RELEASE,
        CANCEL_RELEASE,
        REFRESH,
        BACK,
        HUB
    }
}
