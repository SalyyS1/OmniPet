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
import org.bukkit.util.Vector;
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

    @Override
    public void smoothMove(EntityRef carrier, RuntimeTransform transform, PaperHeadRendererSettings settings) {
        Entity nativeCarrier = entity(carrier);
        Location current = nativeCarrier.getLocation();
        Vector velocity = new Vector(
                transform.position().x() - current.getX(),
                transform.position().y() - current.getY(),
                transform.position().z() - current.getZ()).multiply(settings.movementGain());
        if (velocity.lengthSquared() > settings.maximumVelocity() * settings.maximumVelocity()) {
            velocity.normalize().multiply(settings.maximumVelocity());
        }
        nativeCarrier.setVelocity(velocity);
        nativeCarrier.setRotation(transform.yaw(), transform.pitch());
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
                new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
        Interaction target = interaction(interaction);
        float width = Math.max(0.25f, Math.min(8.0f, scale));
        target.setInteractionWidth(width);
        target.setInteractionHeight(Math.max(0.25f, Math.min(8.0f, scale * 1.25f)));
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
