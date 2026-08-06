package io.github.salyvn.omnipet.paper.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;

/**
 * The reflective bridge to ModelEngine, against stubs shaped to the real R4 API.
 *
 * <p>This is where the whole cluster of ModelEngine symptoms came from, and it is one inverted condition.
 * {@code ModeledEntity.addModel} returns the model it <em>replaced</em> — for a pet being spawned there is
 * nothing to replace, so success is {@code Optional.empty()}. The adapter read that empty as a rejection and
 * threw, every ModelEngine pet failed at spawn, the resolver fell back to the built-in head renderer, and
 * the operator was told "ModelEngine rejected the active model".
 *
 * <p>Which is why one bug produced four reports: a MODELENGINE pet rendered as a player head, the head faced
 * the wrong way, the pet looked like its provider had been ignored, and the head icon it was falling back to
 * suddenly mattered — so demanding one at save time looked like a separate defect.
 *
 * <p>The adapter had no test at all before this.
 */
class ReflectiveModelEngineBindingsTest {
    private static final String MODEL = "wolf_model";

    @BeforeEach
    void resetRegistry() {
        ModelEngineAPI.reset();
        ModelEngineAPI.loadBlueprint(MODEL);
    }

    /** The regression that mattered: a first attach reports no previous model and must still succeed. */
    @Test
    void attachingTheFirstModelSucceedsEvenThoughNothingWasReplaced() throws Exception {
        ReflectiveModelEngineBindings bindings = bindings();
        ModeledEntity modeled = new ModeledEntity();
        ActiveModel active = new ActiveModel(MODEL);

        bindings.attach(modeled, active);

        assertEquals(1, modeled.attached().size(), "the model has to actually be attached");
        assertTrue(modeled.attached().containsKey(MODEL));
    }

    /** And the base armor stand is hidden, or the player would see the carrier through the model. */
    @Test
    void attachingHidesTheCarrier() throws Exception {
        ModeledEntity modeled = new ModeledEntity();

        bindings().attach(modeled, new ActiveModel(MODEL));

        assertFalse(modeled.isBaseEntityVisible());
    }

    @Test
    void aBlueprintTheServerHasIsReportedAsLoaded() throws Exception {
        ReflectiveModelEngineBindings bindings = bindings();

        assertTrue(bindings.hasBlueprint(MODEL));
        assertFalse(bindings.hasBlueprint("not_installed"));
    }

    @Test
    void aModelTheServerDoesNotHaveIsRefusedAtCreation() {
        ReflectiveModelEngineBindings bindings = bindings();

        assertThrows(Exception.class, () -> bindings.createActive("not_installed"));
    }

    @Test
    void scalingReachesTheActiveModel() throws Exception {
        ActiveModel active = new ActiveModel(MODEL);

        bindings().scale(active, 2.5);

        assertEquals(2.5, active.scale());
    }

    /**
     * Swapping models destroys the old one and attaches the new.
     *
     * <p>{@code removeModel} detaches without destroying, so the adapter has to destroy the previous model
     * itself — and must do so exactly once, or a swap would leak an active model per rename.
     */
    @Test
    void replacingAModelDestroysTheOldOneAndAttachesTheNew() throws Exception {
        ReflectiveModelEngineBindings bindings = bindings();
        ModeledEntity modeled = new ModeledEntity();
        ActiveModel first = new ActiveModel(MODEL);
        ActiveModel second = new ActiveModel("fox_model");
        bindings.attach(modeled, first);

        bindings.replace(modeled, first, MODEL, second);

        assertTrue(first.destroyed(), "the replaced model has to be destroyed, since removeModel does not");
        assertFalse(second.destroyed());
        assertEquals(1, modeled.attached().size());
        assertTrue(modeled.attached().containsKey("fox_model"));
    }

    @Test
    void destroyingTakesBothTheModelAndTheModeledEntity() throws Exception {
        ReflectiveModelEngineBindings bindings = bindings();
        ModeledEntity modeled = new ModeledEntity();
        ActiveModel active = new ActiveModel(MODEL);

        bindings.destroy(modeled, active);

        assertTrue(active.destroyed());
        assertTrue(modeled.destroyed());
    }

    /** Binding at all is the other half: a renamed class or method upstream surfaces here, not in production. */
    @Test
    void theAdapterBindsAgainstTheApiItClaimsToSupport() {
        assertEquals(ReflectiveModelEngineBindings.class, bindings().getClass());
    }

    private static ReflectiveModelEngineBindings bindings() {
        try {
            return new ReflectiveModelEngineBindings(
                    ReflectiveModelEngineBindingsTest.class.getClassLoader());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("the adapter no longer binds to the ModelEngine API", failure);
        }
    }
}
