package ekuzo.echeloncore.entity.ability;

import ekuzo.echeloncore.entity.Falsekin;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/** Shared rule for the Falsekin's abilities: any mob may use them, but a Falsekin only while it fights in its true form. */
final class FalsekinAbilities {
    private FalsekinAbilities() { }

    /** True when {@code caster} is a mob that is free to start an ability now. */
    static boolean ready(Entity caster) {
        if (!(caster instanceof Mob mob) || Busy.is(mob)) return false;
        return !(mob instanceof Falsekin falsekin) || falsekin.fighting();
    }
}
