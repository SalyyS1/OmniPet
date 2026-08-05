package io.github.salyvn.omnipet.paper.render;

import io.github.salyvn.omnipet.core.runtime.MovementFacing;

/**
 * Which way the head display faces, as opposed to which way the pet is going.
 *
 * <p>Two separate defects met here, and both showed up as "the face is on backwards".
 *
 * <p>The first is that the display had no heading of its own after spawn. A display entity is a passenger
 * of the carrier, and a passenger keeps its own rotation — Minecraft moves it with its vehicle but does not
 * turn it. The renderer set the display's yaw once at spawn and then only ever rotated the carrier, so the
 * head kept pointing whichever way its owner happened to be facing when the pet appeared. The single place
 * that did re-set it, the safety teleport, is why the head sometimes snapped back to correct on its own.
 *
 * <p>The second is that an {@code ItemDisplay} using the {@code HEAD} item transform renders the skull
 * turned away from the display's own heading, so a head aligned with the carrier shows the back of the
 * skull. {@link #FACE_OFFSET_DEGREES} is that correction. It is a property of how the item transform
 * renders rather than something derived, so it is named and explained rather than folded into an
 * expression.
 *
 * <p>Applied to the display alone. The carrier keeps the real heading, so the interaction hitbox, the
 * movement policy's distance checks, and the nameplate all keep working against an untwisted pet.
 */
final class HeadFacing {
    /**
     * Degrees the head display is turned relative to the pet's actual heading.
     *
     * <p>Half a turn: the {@code HEAD} item transform faces the skull away from the display's heading, so
     * without this the pet walks around showing the back of its own head.
     */
    static final float FACE_OFFSET_DEGREES = 180f;

    private HeadFacing() {}

    /**
     * The yaw to give the head display for a pet heading in {@code carrierYaw}.
     *
     * <p>Normalised, so the value is directly comparable to the one already on the entity and a caller can
     * skip a write when nothing turned.
     */
    static float displayYaw(float carrierYaw) {
        return MovementFacing.normalize(carrierYaw + FACE_OFFSET_DEGREES);
    }

    /**
     * Whether the display's heading is far enough out to be worth a packet.
     *
     * <p>Same reasoning as the carrier's own move threshold: a rotation nobody can see costs a data-watcher
     * update per pet per tick to every nearby player. The tolerance is in degrees of yaw rather than in
     * distance because that is what this compares.
     */
    static boolean needsTurn(float currentYaw, float carrierYaw) {
        if (!Float.isFinite(currentYaw)) return true;
        float difference = MovementFacing.normalize(displayYaw(carrierYaw) - currentYaw);
        return Math.abs(difference) > CarrierMotion.SAME_FACING_DEGREES;
    }
}
