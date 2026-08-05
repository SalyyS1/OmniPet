package io.github.salyvn.omnipet.paper.render;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import io.github.salyvn.omnipet.core.runtime.RendererAppearance;
import io.github.salyvn.omnipet.core.runtime.RuntimeTransform;

final class BukkitPaperHeadRendererBackend implements PaperHeadRendererBackend {
    @Override
    public boolean isMainThread() {
        return Bukkit.isPrimaryThread();
    }

    @Override
    public WorldRef ownerWorld(UUID ownerId) {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline() || !owner.isValid()) return null;
        World world = owner.getWorld();
        return new WorldRef(world.getUID(), world);
    }

    @Override
    public boolean targetChunkLoaded(WorldRef world, RuntimeTransform transform) {
        Location target = location(world, transform);
        return nativeWorld(world).isChunkLoaded(target.getBlockX() >> 4, target.getBlockZ() >> 4);
    }

    @Override
    public EntityRef spawnCarrier(WorldRef world, RuntimeTransform transform) {
        ArmorStand carrier = nativeWorld(world).spawn(location(world, transform), ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setCollidable(false);
            stand.setSilent(true);
            stand.setPersistent(false);
        });
        return ref(carrier);
    }

    @Override
    public EntityRef spawnVisual(WorldRef world, RuntimeTransform transform) {
        ItemDisplay display = nativeWorld(world).spawn(location(world, transform), ItemDisplay.class, item -> {
            item.setBillboard(Display.Billboard.FIXED);
            item.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
            item.setPersistent(false);
        });
        return ref(display);
    }

    @Override
    public EntityRef spawnInteraction(WorldRef world, RuntimeTransform transform) {
        Interaction interaction = nativeWorld(world).spawn(location(world, transform), Interaction.class, target -> {
            target.setResponsive(true);
            target.setPersistent(false);
        });
        return ref(interaction);
    }

    @Override
    public void attach(EntityRef carrier, EntityRef passenger) {
        if (!entity(carrier).addPassenger(entity(passenger))) {
            throw new IllegalStateException("HEAD renderer could not attach passenger");
        }
    }

    @Override public boolean valid(EntityRef entity) { return entity(entity).isValid() && !entity(entity).isDead(); }
    @Override public UUID worldId(EntityRef entity) { return entity(entity).getWorld().getUID(); }

    @Override
    public boolean currentChunkLoaded(EntityRef entity) {
        Entity nativeEntity = entity(entity);
        Location location = nativeEntity.getLocation();
        return nativeEntity.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    @Override
    public double distanceSquared(EntityRef entity, RuntimeTransform transform) {
        Location current = entity(entity).getLocation();
        double x = current.getX() - transform.position().x();
        double y = current.getY() - transform.position().y();
        double z = current.getZ() - transform.position().z();
        return x * x + y * y + z * z;
    }

    /**
     * Puts the carrier where the steering controller says it should be.
     *
     * <p>Moved by teleport rather than by {@code setVelocity}. The carrier is a marker armor stand with
     * gravity disabled, which is to say an entity with no movement physics: velocity handed to it is never
     * integrated into a position, so the pet stood still and the only motion a player ever saw was the
     * safety teleport firing once the gap passed {@code safetyDistance}. That is the whole of the "pet
     * does not follow, it just jumps to me when I get far away" report.
     *
     * <p>The smoothness velocity was reaching for comes from the display entity instead: its
     * {@code teleportDuration} makes the client glide between server positions rather than snap, which is
     * a client-side interpolation the carrier's own physics was never needed for.
     */
    @Override
    public void smoothMove(EntityRef carrier, RuntimeTransform transform, PaperHeadRendererSettings settings) {
        Entity nativeCarrier = entity(carrier);
        Location current = nativeCarrier.getLocation();
        if (!CarrierMotion.needsMove(current.getX(), current.getY(), current.getZ(),
                current.getYaw(), current.getPitch(), transform)) {
            return;
        }
        Location target = current.clone();
        target.setX(transform.position().x());
        target.setY(transform.position().y());
        target.setZ(transform.position().z());
        target.setYaw(transform.yaw());
        target.setPitch(transform.pitch());
        // Best-effort: a refused teleport leaves the carrier where it was and the safety policy picks it
        // up on a later tick. Throwing here would tear down a renderer over one dropped frame.
        nativeCarrier.teleport(target);
    }

    @Override
    public void hardTeleport(
            EntityRef carrier,
            EntityRef visual,
            EntityRef interaction,
            WorldRef world,
            RuntimeTransform transform) {
        Entity nativeCarrier = requireValid(carrier);
        Entity nativeVisual = requireValid(visual);
        Entity nativeInteraction = requireValid(interaction);
        nativeCarrier.eject();
        Location target = location(world, transform);
        teleport(nativeCarrier, target);
        teleport(nativeVisual, target);
        teleport(nativeInteraction, target);
        attach(carrier, visual);
        attach(carrier, interaction);
    }

    @Override
    public void updateAppearance(EntityRef visual, RendererAppearance appearance) {
        itemDisplay(visual).setItemStack(PaperHeadItems.create(appearance));
    }

    /**
     * Puts the pet's name above it, or takes it away.
     *
     * <p>Delegated so a model pet and a head pet are labelled identically; see {@link Nameplate} for why the
     * carrier rather than the display carries it.
     */
    @Override
    public void updateName(
            EntityRef carrier, RendererAppearance appearance, PaperHeadRendererSettings settings) {
        Nameplate.apply(entity(carrier), appearance, settings);
    }

    @Override
    public void updateScale(
            EntityRef visual,
            EntityRef interaction,
            RuntimeTransform transform,
            PaperHeadRendererSettings settings) {
        float scale = (float) transform.scale();
        ItemDisplay display = itemDisplay(visual);
        display.setInterpolationDuration(settings.interpolationTicks());
        display.setTeleportDuration(settings.interpolationTicks());
        display.setTransformation(new Transformation(
                new Vector3f(), leanBack(transform, settings), new Vector3f(scale, scale, scale),
                new AxisAngle4f()));
        Interaction target = interaction(interaction);
        float width = Math.max(0.25f, Math.min(8.0f, scale));
        target.setInteractionWidth(width);
        target.setInteractionHeight(Math.max(0.25f, Math.min(8.0f, scale * 1.25f)));
    }

    /**
     * Tips the pet back as it picks up speed, like something leaning into a run.
     *
     * <p>Applied to the display rather than the carrier so it is purely visual: the interaction hitbox and
     * the movement policy's distance checks keep working against an upright position. The lean is around
     * the pet's local left axis, so it reads the same whichever way the pet has turned.
     */
    private static AxisAngle4f leanBack(RuntimeTransform transform, PaperHeadRendererSettings settings) {
        double speed = transform.horizontalSpeed();
        if (speed <= 0.05 || settings.maximumLeanDegrees() <= 0) return new AxisAngle4f();
        double reference = Math.max(0.1, settings.maximumVelocity());
        double fraction = Math.min(1.0, speed / reference);
        float radians = (float) Math.toRadians(settings.maximumLeanDegrees() * fraction);
        return new AxisAngle4f(radians, 1, 0, 0);
    }

    @Override
    public void remove(EntityRef entity) {
        Entity nativeEntity = entity(entity);
        if (nativeEntity.isValid() || !nativeEntity.isDead()) nativeEntity.remove();
    }

    private static World nativeWorld(WorldRef world) { return (World) world.nativeWorld(); }
    private static Entity entity(EntityRef entity) { return (Entity) entity.nativeEntity(); }
    private static ItemDisplay itemDisplay(EntityRef entity) { return (ItemDisplay) entity.nativeEntity(); }
    private static Interaction interaction(EntityRef entity) { return (Interaction) entity.nativeEntity(); }
    private static EntityRef ref(Entity entity) { return new EntityRef(entity.getUniqueId(), entity); }

    private static Entity requireValid(EntityRef reference) {
        Entity entity = entity(reference);
        if (!entity.isValid() || entity.isDead()) throw new IllegalStateException("HEAD renderer entity is invalid");
        return entity;
    }

    private static Location location(WorldRef world, RuntimeTransform transform) {
        return new Location(nativeWorld(world), transform.position().x(), transform.position().y(), transform.position().z(),
                transform.yaw(), transform.pitch());
    }

    private static void teleport(Entity entity, Location target) {
        if (!entity.teleport(target)) throw new IllegalStateException("HEAD renderer hard teleport failed");
    }
}
