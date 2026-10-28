package ekuzo.echeloncore.item;

import ekuzo.crazyworldprogression.progression.equipment.EquipmentSetRegistry;
import ekuzo.echeloncore.EchelonCore;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Echelon's items. The Pathfinder armor is iron-strength armor whose full-set bonus (faster movement, much faster
 * mining, longer block reach) comes from CWP's equipment sets: resources/equipment-sets/pathfinder.yml. The Life
 * Guardian set (guardian_of_life.yml) unlocks the Sanctuary passive of the Oath of the Lifebound shield.
 */
public final class EchelonItems {
    private static final ArmorMaterial PATHFINDER_MATERIAL = new ArmorMaterial(15,
            Map.of(ArmorType.HELMET, 2, ArmorType.CHESTPLATE, 6, ArmorType.LEGGINGS, 5, ArmorType.BOOTS, 2,
                    ArmorType.BODY, 5),
            9, SoundEvents.ARMOR_EQUIP_IRON, 0.0F, 0.0F, ItemTags.REPAIRS_IRON_ARMOR,
            ResourceKey.create(EquipmentAssets.ROOT_ID, EchelonCore.id("pathfinder")));

    /** Guardian of Life: defensive armor, 15 armor points and 2 toughness for the full set. */
    private static final ArmorMaterial GUARDIAN_OF_LIFE_MATERIAL = new ArmorMaterial(25,
            Map.of(ArmorType.HELMET, 2, ArmorType.CHESTPLATE, 6, ArmorType.LEGGINGS, 5, ArmorType.BOOTS, 2,
                    ArmorType.BODY, 5),
            15, SoundEvents.ARMOR_EQUIP_GOLD, 0.5F, 0.0F, ItemTags.REPAIRS_IRON_ARMOR,
            ResourceKey.create(EquipmentAssets.ROOT_ID, EchelonCore.id("guardian_of_life")));

    /** Martyr's Remorse (IDs sage_*): light support armor, 8 armor points for the full set. */
    private static final ArmorMaterial SAGE_MATERIAL = new ArmorMaterial(12,
            Map.of(ArmorType.HELMET, 1, ArmorType.CHESTPLATE, 3, ArmorType.LEGGINGS, 3, ArmorType.BOOTS, 1,
                    ArmorType.BODY, 3),
            20, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, 0.0F, ItemTags.REPAIRS_LEATHER_ARMOR,
            ResourceKey.create(EquipmentAssets.ROOT_ID, EchelonCore.id("sage")));

    /** Icarus' Folly: glass-cannon armor, 12 armor points for the full set. */
    private static final ArmorMaterial ICARUS_FOLLY_MATERIAL = new ArmorMaterial(18,
            Map.of(ArmorType.HELMET, 2, ArmorType.CHESTPLATE, 5, ArmorType.LEGGINGS, 4, ArmorType.BOOTS, 1,
                    ArmorType.BODY, 5),
            18, SoundEvents.ARMOR_EQUIP_GOLD, 0.0F, 0.0F, ItemTags.REPAIRS_GOLD_ARMOR,
            ResourceKey.create(EquipmentAssets.ROOT_ID, EchelonCore.id("icarus_folly")));

    /** Bloodletter's Pact: 12 armor points for the full set. */
    private static final ArmorMaterial BLOODLETTERS_PACT_MATERIAL = new ArmorMaterial(20,
            Map.of(ArmorType.HELMET, 2, ArmorType.CHESTPLATE, 5, ArmorType.LEGGINGS, 4, ArmorType.BOOTS, 1,
                    ArmorType.BODY, 5),
            15, SoundEvents.ARMOR_EQUIP_NETHERITE, 0.0F, 0.0F, ItemTags.REPAIRS_IRON_ARMOR,
            ResourceKey.create(EquipmentAssets.ROOT_ID, EchelonCore.id("bloodletters_pact")));
    /** Stormcaller: 9 armor points for the full set. */
    private static final ArmorMaterial STORMCALLER_MATERIAL = new ArmorMaterial(16,
            Map.of(ArmorType.HELMET, 2, ArmorType.CHESTPLATE, 4, ArmorType.LEGGINGS, 2, ArmorType.BOOTS, 1,
                    ArmorType.BODY, 4),
            18, SoundEvents.ARMOR_EQUIP_CHAIN, 0.0F, 0.0F, ItemTags.REPAIRS_IRON_ARMOR,
            ResourceKey.create(EquipmentAssets.ROOT_ID, EchelonCore.id("stormcaller")));

    public static final Item PATHFINDER_HELMET = armor("pathfinder_helmet", ArmorType.HELMET);
    public static final Item PATHFINDER_CHESTPLATE = armor("pathfinder_chestplate", ArmorType.CHESTPLATE);
    public static final Item PATHFINDER_LEGGINGS = armor("pathfinder_leggings", ArmorType.LEGGINGS);
    public static final Item PATHFINDER_BOOTS = armor("pathfinder_boots", ArmorType.BOOTS);
    /** Accessories (CWP accessory slots, through the crazy-world-progression:accessories item tag). */
    public static final Item PATHFINDER_RING = accessory("pathfinder_ring");
    public static final Item PATHFINDER_NECKLACE = accessory("pathfinder_necklace");

    public static final GeoArmorItem GUARDIAN_OF_LIFE_HELMET = geoArmor("guardian_of_life_helmet", GUARDIAN_OF_LIFE_MATERIAL, ArmorType.HELMET);
    public static final GeoArmorItem GUARDIAN_OF_LIFE_CHESTPLATE = geoArmor("guardian_of_life_chestplate", GUARDIAN_OF_LIFE_MATERIAL, ArmorType.CHESTPLATE);
    public static final GeoArmorItem GUARDIAN_OF_LIFE_LEGGINGS = geoArmor("guardian_of_life_leggings", GUARDIAN_OF_LIFE_MATERIAL, ArmorType.LEGGINGS);
    public static final GeoArmorItem GUARDIAN_OF_LIFE_BOOTS = geoArmor("guardian_of_life_boots", GUARDIAN_OF_LIFE_MATERIAL, ArmorType.BOOTS);
    public static final GeoArmorItem SAGE_HELMET = geoArmor("sage_helmet", SAGE_MATERIAL, ArmorType.HELMET);
    public static final GeoArmorItem SAGE_CHESTPLATE = geoArmor("sage_chestplate", SAGE_MATERIAL, ArmorType.CHESTPLATE);
    public static final GeoArmorItem SAGE_LEGGINGS = geoArmor("sage_leggings", SAGE_MATERIAL, ArmorType.LEGGINGS);
    public static final GeoArmorItem SAGE_BOOTS = geoArmor("sage_boots", SAGE_MATERIAL, ArmorType.BOOTS);
    public static final GeoArmorItem ICARUS_FOLLY_HELMET = geoArmor("icarus_folly_helmet", ICARUS_FOLLY_MATERIAL, ArmorType.HELMET);
    public static final GeoArmorItem ICARUS_FOLLY_CHESTPLATE = geoArmor("icarus_folly_chestplate", ICARUS_FOLLY_MATERIAL,
            ArmorType.CHESTPLATE);
    public static final GeoArmorItem ICARUS_FOLLY_LEGGINGS = geoArmor("icarus_folly_leggings", ICARUS_FOLLY_MATERIAL,
            ArmorType.LEGGINGS);
    public static final GeoArmorItem ICARUS_FOLLY_BOOTS = geoArmor("icarus_folly_boots", ICARUS_FOLLY_MATERIAL, ArmorType.BOOTS);
    public static final Item BLOODLETTERS_PACT_HELMET = armor("bloodletters_pact_helmet", BLOODLETTERS_PACT_MATERIAL,
            ArmorType.HELMET);
    public static final Item BLOODLETTERS_PACT_CHESTPLATE = armor("bloodletters_pact_chestplate",
            BLOODLETTERS_PACT_MATERIAL, ArmorType.CHESTPLATE);
    public static final Item BLOODLETTERS_PACT_LEGGINGS = armor("bloodletters_pact_leggings", BLOODLETTERS_PACT_MATERIAL,
            ArmorType.LEGGINGS);
    public static final Item BLOODLETTERS_PACT_BOOTS = armor("bloodletters_pact_boots", BLOODLETTERS_PACT_MATERIAL,
            ArmorType.BOOTS);
    public static final GeoArmorItem STORMCALLER_HELMET = geoArmor("stormcaller_helmet", STORMCALLER_MATERIAL, ArmorType.HELMET);
    public static final GeoArmorItem STORMCALLER_CHESTPLATE = geoArmor("stormcaller_chestplate", STORMCALLER_MATERIAL,
            ArmorType.CHESTPLATE);
    public static final GeoArmorItem STORMCALLER_LEGGINGS = geoArmor("stormcaller_leggings", STORMCALLER_MATERIAL, ArmorType.LEGGINGS);
    public static final GeoArmorItem STORMCALLER_BOOTS = geoArmor("stormcaller_boots", STORMCALLER_MATERIAL, ArmorType.BOOTS);
    public static final Item STORMCALLER_RING = accessory("stormcaller_ring");
    public static final Item STORMCALLER_NECKLACE = accessory("stormcaller_necklace");
    /** Placeholder accessory: no movement slowdown while blocking with a shield (client side, LocalPlayer mixin). */
    public static final Item STEADFAST_CHARM = accessory("steadfast_charm");
    /** A shield that blocks like the vanilla one; with the Guardian of Life set it carries the Sanctuary passive. */
    public static final OathOfTheLifeboundItem OATH_OF_THE_LIFEBOUND = shield("oath_of_the_lifebound");

    private EchelonItems() { }

    /** Registers the items, their creative tab entries and Echelon's equipment sets with CWP. */
    public static void register() {
        EquipmentSetRegistry.registerSource(EchelonCore.MOD_ID, EchelonCore.MOD_ID, "equipment-sets");
        ResourceKey<CreativeModeTab> combat = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                Identifier.withDefaultNamespace("combat"));
        CreativeModeTabEvents.modifyOutputEvent(combat).register(output -> output.insertAfter(Items.NETHERITE_BOOTS,
                PATHFINDER_HELMET, PATHFINDER_CHESTPLATE, PATHFINDER_LEGGINGS, PATHFINDER_BOOTS, PATHFINDER_RING,
                PATHFINDER_NECKLACE, GUARDIAN_OF_LIFE_HELMET, GUARDIAN_OF_LIFE_CHESTPLATE, GUARDIAN_OF_LIFE_LEGGINGS,
                GUARDIAN_OF_LIFE_BOOTS, SAGE_HELMET, SAGE_CHESTPLATE, SAGE_LEGGINGS, SAGE_BOOTS, ICARUS_FOLLY_HELMET,
                ICARUS_FOLLY_CHESTPLATE, ICARUS_FOLLY_LEGGINGS, ICARUS_FOLLY_BOOTS, BLOODLETTERS_PACT_HELMET,
                BLOODLETTERS_PACT_CHESTPLATE, BLOODLETTERS_PACT_LEGGINGS, BLOODLETTERS_PACT_BOOTS, STORMCALLER_HELMET,
                STORMCALLER_CHESTPLATE, STORMCALLER_LEGGINGS, STORMCALLER_BOOTS, STORMCALLER_RING, STORMCALLER_NECKLACE,
                STEADFAST_CHARM));
        CreativeModeTabEvents.modifyOutputEvent(combat).register(output -> output.insertAfter(Items.SHIELD,
                OATH_OF_THE_LIFEBOUND));
        Sanctuary.initialize();
    }

    private static OathOfTheLifeboundItem shield(String name) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, EchelonCore.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new OathOfTheLifeboundItem(new Item.Properties().durability(504)
                .equippableUnswappable(EquipmentSlot.OFFHAND)
                .delayedComponent(DataComponents.BLOCKS_ATTACKS, registries -> new BlocksAttacks(0.25F, 1.0F,
                        List.of(new BlocksAttacks.DamageReduction(90.0F, Optional.empty(), 0.0F, 1.0F)),
                        new BlocksAttacks.ItemDamageFunction(3.0F, 1.0F, 1.0F),
                        Optional.of(registries.getOrThrow(DamageTypeTags.BYPASSES_SHIELD)),
                        Optional.of(SoundEvents.SHIELD_BLOCK), Optional.of(SoundEvents.SHIELD_BREAK)))
                .component(DataComponents.BREAK_SOUND, SoundEvents.SHIELD_BREAK).setId(key)));
    }

    private static Item accessory(String name) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, EchelonCore.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().stacksTo(1).setId(key)));
    }

    private static GeoArmorItem geoArmor(String name, ArmorMaterial material, ArmorType type) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, EchelonCore.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key,
                new GeoArmorItem(material, type, new Item.Properties().setId(key)));
    }

    private static Item armor(String name, ArmorType type) { return armor(name, PATHFINDER_MATERIAL, type); }

    private static Item armor(String name, ArmorMaterial material, ArmorType type) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, EchelonCore.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key,
                new Item(new Item.Properties().humanoidArmor(material, type).setId(key)));
    }
}
