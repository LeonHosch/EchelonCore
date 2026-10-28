package ekuzo.echeloncore.balance;

import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.KineticWeapon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Echelon's weaker vanilla weapons and armor. Melee values are the attack damage the tooltip shows (the player's base 1
 * included); spears also get a lower charged-attack multiplier. Armor values are the armor points per piece. Ranged nerfs (bow, crossbow, crossbow fireworks) live
 * in ArrowDamageMixin and FireworkDamageMixin.
 */
public final class VanillaWeapons {
    /** Bow arrows: a full charge deals 4 (vanilla 6). */
    public static final double BOW_FACTOR = 4.0 / 6.0;
    /** Crossbow arrows and fireworks deal 5-8 (vanilla arrows 7-11). */
    public static final int CROSSBOW_MIN = 5;
    public static final int CROSSBOW_MAX = 8;

    private static final Map<Item, Double> ATTACK_DAMAGE = Map.ofEntries(
            Map.entry(Items.WOODEN_SWORD, 2.0), Map.entry(Items.GOLDEN_SWORD, 3.0), Map.entry(Items.STONE_SWORD, 2.5),
            Map.entry(Items.COPPER_SWORD, 3.0), Map.entry(Items.IRON_SWORD, 3.5), Map.entry(Items.DIAMOND_SWORD, 4.0),
            Map.entry(Items.NETHERITE_SWORD, 4.5),
            Map.entry(Items.WOODEN_AXE, 3.0), Map.entry(Items.GOLDEN_AXE, 4.0), Map.entry(Items.STONE_AXE, 3.5),
            Map.entry(Items.COPPER_AXE, 4.5), Map.entry(Items.IRON_AXE, 5.0), Map.entry(Items.DIAMOND_AXE, 6.0),
            Map.entry(Items.NETHERITE_AXE, 6.5),
            Map.entry(Items.WOODEN_PICKAXE, 0.5), Map.entry(Items.GOLDEN_PICKAXE, 1.5), Map.entry(Items.STONE_PICKAXE, 1.0),
            Map.entry(Items.COPPER_PICKAXE, 1.5), Map.entry(Items.IRON_PICKAXE, 2.0), Map.entry(Items.DIAMOND_PICKAXE, 2.5),
            Map.entry(Items.NETHERITE_PICKAXE, 2.5),
            Map.entry(Items.WOODEN_SHOVEL, 0.5), Map.entry(Items.GOLDEN_SHOVEL, 1.5), Map.entry(Items.STONE_SHOVEL, 1.0),
            Map.entry(Items.COPPER_SHOVEL, 1.5), Map.entry(Items.IRON_SHOVEL, 2.0), Map.entry(Items.DIAMOND_SHOVEL, 2.5),
            Map.entry(Items.NETHERITE_SHOVEL, 2.5),
            Map.entry(Items.WOODEN_HOE, 0.5), Map.entry(Items.GOLDEN_HOE, 1.5), Map.entry(Items.STONE_HOE, 1.0),
            Map.entry(Items.COPPER_HOE, 1.5), Map.entry(Items.IRON_HOE, 2.0), Map.entry(Items.DIAMOND_HOE, 2.5),
            Map.entry(Items.NETHERITE_HOE, 2.5),
            Map.entry(Items.WOODEN_SPEAR, 1.0), Map.entry(Items.GOLDEN_SPEAR, 1.0), Map.entry(Items.STONE_SPEAR, 1.0),
            Map.entry(Items.COPPER_SPEAR, 1.5), Map.entry(Items.IRON_SPEAR, 1.5), Map.entry(Items.DIAMOND_SPEAR, 2.0),
            Map.entry(Items.NETHERITE_SPEAR, 2.5));

    private static final Map<Item, Double> ARMOR = new HashMap<>();

    static {
        armor(1, 1, 1, 1, Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS);
        armor(1, 2, 1, 1, Items.COPPER_HELMET, Items.COPPER_CHESTPLATE, Items.COPPER_LEGGINGS, Items.COPPER_BOOTS);
        armor(1, 2, 1, 1, Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS);
        armor(1, 2, 2, 1, Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS);
        armor(2, 3, 2, 1, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
        armor(2, 4, 2, 2, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
        armor(2, 4, 3, 2, Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);
    }

    /** Charged (kinetic) spear attack multiplier per material. */
    private static final Map<Item, Float> SPEAR_CHARGE = Map.of(
            Items.WOODEN_SPEAR, 0.5F, Items.GOLDEN_SPEAR, 0.5F, Items.STONE_SPEAR, 0.5F, Items.COPPER_SPEAR, 0.5F,
            Items.IRON_SPEAR, 0.7F, Items.DIAMOND_SPEAR, 0.7F, Items.NETHERITE_SPEAR, 0.8F);

    private VanillaWeapons() { }

    public static void register() {
        DefaultItemComponentEvents.MODIFY.register(context -> {
            context.modify(ATTACK_DAMAGE::containsKey, (builder, item) -> {
                ItemAttributeModifiers modifiers = item.components().get(DataComponents.ATTRIBUTE_MODIFIERS);
                if (modifiers != null) builder.set(DataComponents.ATTRIBUTE_MODIFIERS, withValue(modifiers, Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE.get(item) - 1.0));
            });
            context.modify(ARMOR::containsKey, (builder, item) -> {
                ItemAttributeModifiers modifiers = item.components().get(DataComponents.ATTRIBUTE_MODIFIERS);
                if (modifiers != null) builder.set(DataComponents.ATTRIBUTE_MODIFIERS, withValue(modifiers, Attributes.ARMOR, ARMOR.get(item)));
            });
            context.modify(SPEAR_CHARGE::containsKey, (builder, item) -> {
                KineticWeapon kinetic = item.components().get(DataComponents.KINETIC_WEAPON);
                if (kinetic != null) builder.set(DataComponents.KINETIC_WEAPON, new KineticWeapon(kinetic.contactCooldownTicks(),
                        kinetic.delayTicks(), kinetic.dismountConditions(), kinetic.knockbackConditions(), kinetic.damageConditions(),
                        kinetic.forwardMovement(), SPEAR_CHARGE.get(item), kinetic.sound(), kinetic.hitSound()));
            });
        });
    }

    private static void armor(double head, double chest, double legs, double feet, Item... pieces) {
        double[] values = {head, chest, legs, feet};
        for (int i = 0; i < pieces.length; i++) ARMOR.put(pieces[i], values[i]);
    }

    /** The item's modifiers with its {@code attribute} modifier set to {@code value}. */
    private static ItemAttributeModifiers withValue(ItemAttributeModifiers modifiers, Holder<Attribute> attribute, double value) {
        List<ItemAttributeModifiers.Entry> entries = new ArrayList<>();
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (!entry.attribute().equals(attribute)) {
                entries.add(entry);
                continue;
            }
            AttributeModifier old = entry.modifier();
            entries.add(new ItemAttributeModifiers.Entry(entry.attribute(),
                    new AttributeModifier(old.id(), value, old.operation()), entry.slot(), entry.display()));
        }
        return new ItemAttributeModifiers(entries);
    }
}
