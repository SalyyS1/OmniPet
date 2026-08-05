package io.github.salyvn.omnipet.paper.incubation.placed;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * The order in which a spent support item is taken and its effect written.
 *
 * <p>Asserted against the source because the two operations touch a live player inventory and a real disk,
 * and no unit test can observe a crash between them. What can be checked is that the code cannot have been
 * reordered: the durable write must appear before the item is consumed.
 *
 * <p>The asymmetry is the point. Write-then-take can only fail towards the player — a crash in between
 * leaves them holding an item whose effect already landed. Take-then-write fails the other way and destroys
 * something they paid for. A future refactor that "tidies" these two lines into the natural-reading order
 * would silently pick the second, so it fails the build instead.
 */
class PlacedEggSupportOrderingTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/io/github/salyvn/omnipet/paper/incubation/placed",
            "PlacedEggSupportController.java");

    @Test
    void theReductionIsWrittenBeforeTheItemIsTaken() throws Exception {
        String source = code(SOURCE);

        int write = source.indexOf("coordinator.applyReduction(");
        int take = source.indexOf("consumeOne(player,");

        assertTrue(write >= 0, "the controller must credit the effect through the coordinator");
        assertTrue(take >= 0, "the controller must consume the spent item");
        assertTrue(write < take,
                "the effect must be durable before the item is taken; the reverse order loses a paid item");
    }

    /** A failed write returns empty, and the controller must stop there rather than take the item anyway. */
    @Test
    void aRefusedWriteReturnsWithoutTakingTheItem() throws Exception {
        String source = code(SOURCE);

        int guard = source.indexOf("advanced.isEmpty()");
        int take = source.indexOf("consumeOne(player,");

        assertTrue(guard >= 0, "the controller must check whether the reduction was written");
        assertTrue(guard < take, "the empty check has to come before the item is consumed");
    }

    /** Every refusal is decided from re-read state, so a minutes-old menu cannot act on what it shows. */
    @Test
    void theSpendPathRereadsTheRecordRatherThanTrustingTheOpenMenu() throws Exception {
        String source = code(SOURCE);

        assertTrue(source.contains("coordinator.byKey(holder.recordKey())"),
                "the record must be looked up again by key");
        // Scoped to the spend path: the negated ownership check is unique to it, while the plain form also
        // appears in open(), which decides whether to draw buttons at all.
        int lookup = source.indexOf("Optional<PlacedEggRecord> found = find(holder)");
        int ownerCheck = source.indexOf("if (!record.ownerId().equals(player.getUniqueId()))");
        int readyCheck = source.indexOf("if (record.ready())");
        assertTrue(lookup >= 0, "the spend path must re-read the record");
        assertTrue(ownerCheck > lookup,
                "ownership must be checked against the re-read record, not the holder");
        assertTrue(readyCheck > lookup,
                "readiness must be checked against the re-read record, not the drawn countdown");
    }

    /** The menu holds no items, so a click on it must be cancelled before anything is dispatched. */
    @Test
    void theMenuCancelsEveryClickBeforeDispatchingOne() throws Exception {
        String listener = code(Path.of(
                "src/main/java/io/github/salyvn/omnipet/paper/gui/egg", "PlacedEggMenuListener.java"));

        int cancel = listener.indexOf("event.setCancelled(true)");
        int dispatch = listener.indexOf("controller.click(");

        assertTrue(cancel >= 0 && dispatch > cancel,
                "an uncancelled click in a menu with no storage is a way to lose an item");
    }

    /** Comments mention the rejected order by name, so they must not be what satisfies these assertions. */
    private static String code(Path file) throws Exception {
        return Files.readString(file)
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
    }
}
