package io.github.salyvn.omnipet.paper.render;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;

import io.github.salyvn.omnipet.core.runtime.MovementGait;
import io.github.salyvn.omnipet.core.runtime.PetRendererPort;
import io.github.salyvn.omnipet.core.runtime.RendererAppearance;
import io.github.salyvn.omnipet.core.runtime.RendererHandle;
import io.github.salyvn.omnipet.core.runtime.RendererHealth;
import io.github.salyvn.omnipet.core.runtime.RendererSpawnRequest;
import io.github.salyvn.omnipet.core.runtime.RuntimeTransform;

/** ModelEngine R4.0.9 renderer with disposable Bukkit carrier and HEAD fallback upstream. */
public final class PaperModelEngineRenderer implements PetRendererPort {
    private final ReflectiveModelEngineBindings bindings;
    private final ReflectiveModelEngineAnimationBindings animations;
    private final ModelEngineAnimations clips;
    private final PaperHeadRendererSettings settings;
    private final Map<UUID, ModelEngineRendererHandle> handles = new LinkedHashMap<>();
    private String unhealthy;

    public PaperModelEngineRenderer(ClassLoader providerLoader) {
        this(providerLoader, PaperHeadRendererSettings.defaults());
    }

    /**
     * Shares the built-in renderer's movement settings.
     *
     * <p>This adapter used to hold its own copies of the gain, the velocity ceiling, and the safety
     * distance, so retuning one renderer silently left the other at the old numbers.
     */
    public PaperModelEngineRenderer(ClassLoader providerLoader, PaperHeadRendererSettings settings) {
        this(providerLoader, settings, ModelEngineAnimations.defaults());
    }

    /**
     * Shares the built-in renderer's movement settings and takes the clip names to drive.
     *
     * <p>This adapter used to hold its own copies of the gain, the velocity ceiling, and the safety
     * distance, so retuning one renderer silently left the other at the old numbers.
     */
    public PaperModelEngineRenderer(
            ClassLoader providerLoader,
            PaperHeadRendererSettings settings,
            ModelEngineAnimations clips) {
        this.settings = java.util.Objects.requireNonNull(settings, "renderer settings");
        this.clips = java.util.Objects.requireNonNull(clips, "animation clip names");
        ReflectiveModelEngineBindings resolved = null;
        try {
            resolved = new ReflectiveModelEngineBindings(providerLoader);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            unhealthy = detail(failure);
        }
        bindings = resolved;
        // Bound separately and allowed to fail: losing the animation API must cost clips, not the model.
        animations = new ReflectiveModelEngineAnimationBindings(providerLoader);
    }

    /** Why animation is off on this server, or null when clips are being driven. */
    public String animationUnavailableDetail() {
        return animations.unavailableDetail();
    }

    @Override
    public RendererHealth health() {
        return unhealthy == null && bindings != null
                ? new RendererHealth(RendererHealth.Status.AVAILABLE, "ModelEngine R4.0.9 adapter")
                : new RendererHealth(RendererHealth.Status.QUARANTINED,
                        unhealthy == null ? "ModelEngine API is unavailable" : unhealthy);
    }

    @Override
    public RendererHandle spawn(RendererSpawnRequest request) {
        requireMainThread();
        requireHealthy();
        if (!request.appearance().provider().equals("MODELENGINE")) {
            throw new IllegalArgumentException("ModelEngine renderer requires MODELENGINE appearance");
        }
        ModelEngineRendererHandle existing = handles.get(request.petInstanceId());
        if (existing != null && !existing.removed()) return existing;
        Player owner = requireOwner(request.ownerId());
        Location target = location(owner.getWorld(), request.transform());
        requireLoaded(target);
        ArmorStand carrier = null;
        ArmorStand plate = null;
        Interaction interaction = null;
        Object modeled = null;
        Object active = null;
        try {
            if (!bindings.hasBlueprint(request.appearance().assetId())) {
                throw new IllegalStateException("ModelEngine model is not loaded: " + request.appearance().assetId());
            }
            carrier = owner.getWorld().spawn(target, ArmorStand.class, stand -> {
                stand.setInvisible(true);
                // A marker, like the HEAD renderer's carrier. ModelEngine sets its own cull hitbox from
                // the blueprint when the model is attached, so the carrier's own bounding box is not what
                // decides whether a client draws the model — an earlier guess that it was turned out to be
                // wrong, and the real cause of invisible ModelEngine pets was the attach call below.
                stand.setMarker(true);
                stand.setGravity(false);
                stand.setInvulnerable(true);
                stand.setCollidable(false);
                stand.setSilent(true);
                stand.setPersistent(false);
            });
            plate = spawnPlate(owner.getWorld(), target);
            interaction = owner.getWorld().spawn(target, Interaction.class, entity -> {
                entity.setResponsive(true);
                entity.setPersistent(false);
                setInteractionScale(entity, request.transform().scale());
            });
            if (!carrier.addPassenger(interaction)) throw new IllegalStateException("could not attach model interaction");
            if (!carrier.addPassenger(plate)) throw new IllegalStateException("could not attach model nameplate");
            modeled = bindings.createModeled(carrier);
            active = bindings.createActive(request.appearance().assetId());
            bindings.scale(active, request.transform().scale());
            bindings.attach(modeled, active);
            ModelEngineRendererHandle handle = new ModelEngineRendererHandle(
                    request.ownerId(), request.petInstanceId(), request.rendererGeneration(),
                    carrier, plate, interaction, modeled, active, request.appearance().assetId(),
                    request.transform(), animations.available(), request.appearance());
            handles.put(request.petInstanceId(), handle);
            Nameplate.apply(plate, request.appearance(), settings);
            driveAnimation(handle, request.transform());
            return handle;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            cleanup(modeled, active, interaction, plate, carrier, failure);
            quarantine(failure);
            throw wrap(failure);
        }
    }

    /**
     * The stand the nameplate is written to.
     *
     * <p>A separate entity because ModelEngine hides the carrier by despawning it client-side rather than
     * by making it transparent, so a name on the carrier reaches no one. This one is invisible the vanilla
     * way, which leaves its plate renderable, and rides the carrier so it tracks the model for free.
     *
     * <p>Not a marker: a marker armor stand has a zero-height bounding box, and the plate is drawn above
     * that box, so it would sit inside the model instead of over it.
     */
    private static ArmorStand spawnPlate(World world, Location target) {
        return world.spawn(target, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setSmall(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setCollidable(false);
            stand.setSilent(true);
            stand.setPersistent(false);
            stand.setCanPickupItems(false);
        });
    }

    @Override
    public void update(RendererHandle raw, RuntimeTransform transform) {
        requireMainThread();
        ModelEngineRendererHandle handle = requireHandle(raw);
        Player owner = requireOwner(handle.ownerId());
        Location target = location(owner.getWorld(), transform);
        requireLoaded(target);
        ArmorStand carrier = handle.carrier();
        if (!carrier.isValid() || !handle.interaction().isValid() || !handle.plate().isValid()) {
            retire(handle);
            throw new IllegalStateException("ModelEngine renderer entities became invalid");
        }
        if (!carrier.getWorld().equals(owner.getWorld())
                || carrier.getLocation().distanceSquared(target)
                        > settings.safetyDistance() * settings.safetyDistance()) {
            carrier.eject();
            if (!carrier.teleport(target) || !handle.interaction().teleport(target)
                    || !handle.plate().teleport(target)) {
                throw new IllegalStateException("ModelEngine renderer safety teleport failed");
            }
            if (!carrier.addPassenger(handle.interaction())) {
                throw new IllegalStateException("could not reattach model interaction");
            }
            // The plate rides the carrier too, and ejecting dropped it along with the interaction. Missing
            // this would leave the name floating where the pet was teleported from.
            if (!carrier.addPassenger(handle.plate())) {
                throw new IllegalStateException("could not reattach model nameplate");
            }
        } else {
            // Teleport, not setVelocity. The carrier is a marker armor stand with gravity off, so it has
            // no movement physics to integrate a velocity into: the model stood still and only moved when
            // the safety distance tripped. ModelEngine interpolates the model between server positions
            // itself, which is where the smoothness velocity was reaching for actually comes from.
            Location current = carrier.getLocation();
            if (CarrierMotion.needsMove(current.getX(), current.getY(), current.getZ(),
                    current.getYaw(), current.getPitch(), transform)) {
                Location destination = target.clone();
                destination.setYaw(transform.yaw());
                destination.setPitch(transform.pitch());
                // Best-effort: a refused teleport leaves the carrier alone and the safety branch above
                // picks it up on a later tick rather than tearing the renderer down over one frame.
                carrier.teleport(destination);
            }
        }
        // Only when the size actually changed. setScale is a reflective call and the interaction's width
        // and height are data-watcher fields, so re-applying an unchanged scale cost a metadata packet per
        // pet per tick to every nearby player for no visible difference.
        if (handle.scaleChanged(transform)) {
            try {
                bindings.scale(handle.activeModel(), transform.scale());
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                quarantine(failure);
                throw wrap(failure);
            }
            setInteractionScale(handle.interaction(), transform.scale());
        }
        driveAnimation(handle, transform);
        // Only when the word itself changes: the status comes from speed, which moves every tick, but
        // resolves to one of four words, so a walking pet would otherwise rewrite an identical plate
        // twenty times a second to every player nearby.
        io.github.salyvn.omnipet.core.runtime.PetStatus status =
                io.github.salyvn.omnipet.core.runtime.PetStatus.of(transform, settings.maximumVelocity());
        if (handle.statusChanged(status) && handle.appearance() != null) {
            Nameplate.apply(handle.plate(), handle.appearance(), settings, status);
        }
        handle.transform(transform);
    }

    /**
     * Switches the clip when the pet's gait changes.
     *
     * <p>Only on a change: re-issuing the playing clip every tick would restart it, so a walking pet would
     * never get past the first frame. Best-effort by design — a definition naming a clip the model does not
     * have loses its animation and keeps its model, reported once rather than every tick.
     *
     * <p>An idle flourish outranks the gait clip while it runs, and the gait clip resumes on its own when
     * the flourish ends, because by then this method sees no flourish and asks for the gait again.
     */
    private void driveAnimation(ModelEngineRendererHandle handle, RuntimeTransform transform) {
        if (!animations.available()) return;
        String clip = clips.forFlourish(transform.idle().flourish());
        if (clip == null) {
            MovementGait gait = MovementGait.of(
                    transform.horizontalSpeed(), transform.dashing(), settings.maximumVelocity(),
                    transform.resting());
            clip = clips.forGait(gait);
        }
        if (clip == null || clip.equals(handle.playingAnimation())) return;
        if (animations.play(handle.activeModel(), handle.playingAnimation(), clip)) {
            handle.playingAnimation(clip);
        }
    }

    @Override
    public void updateAppearance(RendererHandle raw, RendererAppearance appearance) {
        requireMainThread();
        ModelEngineRendererHandle handle = requireHandle(raw);
        // Before the asset check, because a rename changes the name and not the model: returning early on
        // an unchanged asset ID would make renaming a ModelEngine pet do nothing.
        // Written with the status the pet already has, or the rename would drop the status word until the
        // pet next changed gait.
        handle.appearance(appearance);
        Nameplate.apply(handle.plate(), appearance, settings,
                io.github.salyvn.omnipet.core.runtime.PetStatus.of(
                        handle.transform(), settings.maximumVelocity()));
        if (handle.assetId().equals(appearance.assetId())) return;
        try {
            if (!bindings.hasBlueprint(appearance.assetId())) {
                throw new IllegalStateException("ModelEngine model is not loaded: " + appearance.assetId());
            }
            Object next = bindings.createActive(appearance.assetId());
            bindings.scale(next, handle.transform().scale());
            bindings.replace(handle.modeledEntity(), handle.activeModel(), handle.assetId(), next);
            handle.activeModel(next);
            handle.assetId(appearance.assetId());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            quarantine(failure);
            throw wrap(failure);
        }
    }

    @Override
    public void remove(RendererHandle raw) {
        requireMainThread();
        ModelEngineRendererHandle handle = requireHandle(raw);
        retire(handle);
    }

    private void retire(ModelEngineRendererHandle handle) {
        Throwable failure = null;
        try {
            bindings.destroy(handle.modeledEntity(), handle.activeModel());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            failure = error;
        }
        handle.interaction().remove();
        handle.plate().remove();
        handle.carrier().remove();
        handle.markRemoved();
        handles.remove(handle.petInstanceId(), handle);
        if (failure != null) throw wrap(failure);
    }

    private ModelEngineRendererHandle requireHandle(RendererHandle raw) {
        if (!(raw instanceof ModelEngineRendererHandle handle) || handle.removed()
                || handles.get(handle.petInstanceId()) != handle) {
            throw new IllegalArgumentException("ModelEngine renderer handle is invalid or removed");
        }
        return handle;
    }

    private static Player requireOwner(UUID ownerId) {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline() || !owner.isValid()) {
            throw new IllegalStateException("ModelEngine owner is unavailable");
        }
        return owner;
    }

    private static Location location(World world, RuntimeTransform transform) {
        return new Location(world, transform.position().x(), transform.position().y(), transform.position().z(),
                transform.yaw(), transform.pitch());
    }

    private static void requireLoaded(Location target) {
        if (!target.getWorld().isChunkLoaded(target.getBlockX() >> 4, target.getBlockZ() >> 4)) {
            throw new IllegalStateException("ModelEngine target chunk is not loaded");
        }
    }

    private static void setInteractionScale(Interaction interaction, double scale) {
        interaction.setInteractionWidth((float) Math.max(0.25, Math.min(8, scale)));
        interaction.setInteractionHeight((float) Math.max(0.25, Math.min(8, scale * 1.25)));
    }

    private void cleanup(
            Object modeled, Object active, Interaction interaction, ArmorStand plate, ArmorStand carrier,
            Throwable primary) {
        if (modeled != null && active != null) {
            try { bindings.destroy(modeled, active); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError cleanup) { primary.addSuppressed(cleanup); }
        }
        if (interaction != null) interaction.remove();
        if (plate != null) plate.remove();
        if (carrier != null) carrier.remove();
    }

    private void requireHealthy() {
        if (unhealthy != null || bindings == null) throw new IllegalStateException(health().detail());
    }

    private void quarantine(Throwable failure) { unhealthy = "adapter quarantined: " + detail(failure); }
    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("ModelEngine renderer requires the Paper main thread");
    }
    private static RuntimeException wrap(Throwable failure) {
        return failure instanceof RuntimeException runtime ? runtime : new IllegalStateException(detail(failure), failure);
    }
    private static String detail(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
