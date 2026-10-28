package ekuzo.echeloncore.entity.ability;

import ekuzo.crazyworldprogression.api.mobforge.MobDefinition;
import ekuzo.crazyworldprogression.api.mobforge.MobForgeApi;
import ekuzo.echeloncore.EchelonCore;
import ekuzo.echeloncore.entity.EchelonEntities;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.entity.Mob;

/**
 * Echelon's mob abilities, registered with CWP's MobForge so any mob can use them: through a template's
 * {@code abilities:} list, or cast by a script ({@code mob_cast}). The Leshen's MobForge definition makes Shadow Step
 * and Crow Swarm its default abilities.
 */
public final class EchelonAbilities {
    private EchelonAbilities() { }

    public static void register() {
        // A mob in the middle of an ability (shadow-stepping, casting, winding up) can't hit anything with its own
        // attacks; the ability itself (crows, roots, the slam) still can.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(source.getDirectEntity() instanceof Mob mob
                && Busy.is(mob)));
        MobForgeApi.abilities().register(ShadowStep.ID, ShadowStep::new);
        MobForgeApi.abilities().register(CrowSwarmAbility.ID, CrowSwarmAbility::new);
        CrowSwarmAbility.initialize();
        MobForgeApi.abilities().register(RootSnareAbility.ID, RootSnareAbility::new);
        MobForgeApi.abilities().register(GroundSlam.ID, GroundSlam::new);
        MobForgeApi.abilities().register(SummonWolves.ID, SummonWolves::new);
        MobForgeApi.abilities().register(SkinShed.ID, SkinShed::new);
        MobForgeApi.abilities().register(StolenVoice.ID, StolenVoice::new);
        MobForgeApi.abilities().register(TongueLash.ID, TongueLash::new);
        MobForgeApi.abilities().register(Flay.ID, Flay::new);
        MobForgeApi.abilities().register(MassDecoyAbility.ID, MassDecoyAbility::new);
        // Default abilities, used on its own in combat (Ground Slam when a target is within reach, and also right
        // after some Shadow Steps); Summon Wolves the Leshen uses through its second phase.
        MobForgeApi.mobs().register(MobDefinition.builder(EchelonCore.id("leshen"), EchelonEntities.LESHEN)
                .displayName("Leshen").ability(ShadowStep.ID).ability(CrowSwarmAbility.ID).ability(RootSnareAbility.ID)
                .ability(GroundSlam.ID).build());
        // The Falsekin uses these only while fighting in its true form (once found out or snapped).
        MobForgeApi.mobs().register(MobDefinition.builder(EchelonCore.id("falsekin"), EchelonEntities.FALSEKIN)
                .displayName("Falsekin").ability(SkinShed.ID).ability(StolenVoice.ID).ability(TongueLash.ID).ability(MassDecoyAbility.ID)
                .ability(Flay.ID).build());
    }
}
