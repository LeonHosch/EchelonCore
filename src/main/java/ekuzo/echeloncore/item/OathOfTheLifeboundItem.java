package ekuzo.echeloncore.item;

import net.minecraft.world.item.ShieldItem;

/**
 * Functional shield item. It renders through a plain item model (assets/.../models/item/oath_of_the_lifebound.json)
 * laid out like the vanilla shield. It must be a ShieldItem: the first-person hand renderer only skips the old
 * sword-blocking pose for ShieldItems.
 */
public final class OathOfTheLifeboundItem extends ShieldItem {
    public OathOfTheLifeboundItem(Properties properties) {
        super(properties);
    }
}
