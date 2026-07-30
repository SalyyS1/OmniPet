package io.github.salyvn.omnipet.paper.studio.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StudioDraftInputParsersTest {
    @Test
    void parsesEveryEditorFieldWithoutVendorApis() {
        assertEquals("TEXTURE_URL", StudioDraftInputParsers.icon("TEXTURE_URL https://textures.minecraft.net/texture/x").source());
        assertEquals("MODELENGINE", StudioDraftInputParsers.display("MODELENGINE dragon").provider().name());
        assertEquals(1, StudioDraftInputParsers.stats("ATTACK_DAMAGE FLAT 10 50").size());
        assertEquals(1, StudioDraftInputParsers.rarity("COMMON 0 100 1 1.25").size());
        assertEquals(50, StudioDraftInputParsers.progression("50;level * 2").maxLevel());
        assertEquals(1, StudioDraftInputParsers.skills("MYTHICMOBS:omnipet:dash|ACTIVE|5s|0.5|2|OWNER").size());
        assertEquals("follow", StudioDraftInputParsers.behavior("mode=follow").get("mode"));
        assertEquals("MANUAL", StudioDraftInputParsers.release("manual").mode());
    }

    @Test
    void rejectsMalformedEditorInput() {
        assertThrows(IllegalArgumentException.class, () -> StudioDraftInputParsers.icon("HEAD nope"));
        assertThrows(IllegalArgumentException.class, () -> StudioDraftInputParsers.stats("ATTACK_DAMAGE FLAT 50 10"));
        assertThrows(IllegalArgumentException.class, () -> StudioDraftInputParsers.rarity("COMMON 100 0 1 1"));
        assertThrows(IllegalArgumentException.class, () -> StudioDraftInputParsers.progression("50;level + unknown"));
        assertThrows(IllegalArgumentException.class, () -> StudioDraftInputParsers.skills("MYTHICMOBS:dash|ACTIVE|bad|1|0|OWNER"));
    }
}
