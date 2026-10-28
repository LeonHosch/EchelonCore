package ekuzo.echeloncore.entity;

import ekuzo.echeloncore.EchelonCore;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

/** Echelon's custom mobs and their spawn eggs. */
public final class EchelonEntities {
    public static final EntityType<Leshen> LESHEN = register("leshen",
            EntityType.Builder.of(Leshen::new, MobCategory.MONSTER).sized(1.2F, 3.4F).eyeHeight(2.9F).clientTrackingRange(10));
    public static final EntityType<CorruptedStag> CORRUPTED_STAG = register("corrupted_stag",
            EntityType.Builder.of(CorruptedStag::new, MobCategory.MONSTER).sized(1.5F, 2.6F).eyeHeight(2.4F).clientTrackingRange(10));
    public static final EntityType<CrowSwarm> CROW_SWARM = register("crow_swarm",
            EntityType.Builder.<CrowSwarm>of(CrowSwarm::new, MobCategory.MISC).sized(1.2F, 1.2F).noSave().noSummon()
                    .clientTrackingRange(8).updateInterval(1));
    public static final EntityType<RootSnare> ROOT_SNARE = register("root_snare",
            EntityType.Builder.<RootSnare>of(RootSnare::new, MobCategory.MISC).sized(4.8F, 1.0F).noSave().noSummon()
                    .clientTrackingRange(8).updateInterval(2));
    public static final EntityType<ShadowWolf> SHADOW_WOLF = register("shadow_wolf",
            EntityType.Builder.of(ShadowWolf::new, MobCategory.MONSTER).sized(0.9F, 1.1F).eyeHeight(0.9F).noSave()
                    .clientTrackingRange(10));
    public static final EntityType<Falsekin> FALSEKIN = register("falsekin",
            EntityType.Builder.of(Falsekin::new, MobCategory.MONSTER).sized(1.1F, 2.4F).eyeHeight(1.6F).noSave()
                    .clientTrackingRange(10));
    /**
     * The Corrupted Stag as a mount and the Leshen as a pet (CWP collectibles). The mount type must save (players can
     * only ride saveable entities); a loaded mount has no owner and removes itself.
     */
    public static final EntityType<StagMount> STAG_MOUNT = register("stag_mount",
            EntityType.Builder.of(StagMount::new, MobCategory.MISC).sized(1.4F, 1.9F).passengerAttachments(1.75F)
                    .clientTrackingRange(10));
    public static final EntityType<LeshenPet> LESHEN_PET = register("leshen_pet",
            EntityType.Builder.of(LeshenPet::new, MobCategory.MISC).sized(0.35F, 0.85F).eyeHeight(0.72F).noSave()
                    .clientTrackingRange(10));
    public static final Item LESHEN_SPAWN_EGG = spawnEgg("leshen_spawn_egg", LESHEN);
    public static final Item CORRUPTED_STAG_SPAWN_EGG = spawnEgg("corrupted_stag_spawn_egg", CORRUPTED_STAG);
    public static final Item FALSEKIN_SPAWN_EGG = spawnEgg("falsekin_spawn_egg", FALSEKIN);

    private EchelonEntities() { }

    public static void register() {
        EchelonTasks.initialize();
        ekuzo.echeloncore.entity.ability.EchelonAbilities.register();
        FalsekinHaunts.initialize();
        EchelonParticles.register();
        FabricDefaultAttributeRegistry.register(LESHEN, Leshen.createAttributes());
        FabricDefaultAttributeRegistry.register(CORRUPTED_STAG, CorruptedStag.createAttributes());
        FabricDefaultAttributeRegistry.register(SHADOW_WOLF, ShadowWolf.createAttributes());
        FabricDefaultAttributeRegistry.register(FALSEKIN, Falsekin.createAttributes());
        FabricDefaultAttributeRegistry.register(STAG_MOUNT, StagMount.createMountAttributes());
        FabricDefaultAttributeRegistry.register(LESHEN_PET, LeshenPet.createPetAttributes());
        ekuzo.crazyworldprogression.collectibles.Collectibles.registerMount(new ekuzo.crazyworldprogression.collectibles.Collectibles.MountDefinition(
                ekuzo.echeloncore.EchelonCore.id("corrupted_stag"), "Corrupted Stag", ekuzo.crazyworldprogression.collectibles.Rarity.EPIC,
                STAG_MOUNT, 0.3, 0.9));
        ekuzo.crazyworldprogression.collectibles.Collectibles.registerPet(new ekuzo.crazyworldprogression.collectibles.Collectibles.PetDefinition(
                ekuzo.echeloncore.EchelonCore.id("leshen"), "Little Leshen", ekuzo.crazyworldprogression.collectibles.Rarity.RARE, LESHEN_PET));
        ResourceKey<CreativeModeTab> eggs = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                Identifier.withDefaultNamespace("spawn_eggs"));
        CreativeModeTabEvents.modifyOutputEvent(eggs).register(output -> {
            output.accept(LESHEN_SPAWN_EGG);
            output.accept(CORRUPTED_STAG_SPAWN_EGG);
            output.accept(FALSEKIN_SPAWN_EGG);
        });
    }

    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, EchelonCore.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    private static Item spawnEgg(String name, EntityType<?> type) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, EchelonCore.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new SpawnEggItem(new Item.Properties().spawnEgg(type).setId(key)));
    }
}
