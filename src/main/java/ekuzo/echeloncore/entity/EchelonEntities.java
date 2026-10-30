package ekuzo.echeloncore.entity;

import ekuzo.echeloncore.EchelonCore;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import ekuzo.crazyworldprogression.collectibles.LookMount;
import ekuzo.crazyworldprogression.collectibles.MiniPet;
import ekuzo.crazyworldprogression.collectibles.Rarity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
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
     * The Corrupted Stag and the Unicorn as mounts and the Leshen as a pet (CWP collectibles). The mount type must save (players can
     * only ride saveable entities); a loaded mount has no owner and removes itself.
     */
    public static final EntityType<StagMount> STAG_MOUNT = register("stag_mount",
            EntityType.Builder.of(StagMount::new, MobCategory.MISC).sized(1.4F, 1.9F).passengerAttachments(1.75F)
                    .clientTrackingRange(10));
    /** The Void Wraith of the Fallen Star event: hovers and fires lasers through blocks. */
    public static final EntityType<VoidWraith> VOID_WRAITH = register("void_wraith",
            EntityType.Builder.of(VoidWraith::new, MobCategory.MONSTER).sized(0.9F, 2.1F).eyeHeight(1.8F)
                    .clientTrackingRange(10));
    public static final EntityType<UnicornMount> UNICORN_MOUNT = register("unicorn_mount",
            EntityType.Builder.of(UnicornMount::new, MobCategory.MISC).sized(1.4F, 1.7F).passengerAttachments(1.45F)
                    .clientTrackingRange(10));
    public static final EntityType<LeshenPet> LESHEN_PET = register("leshen_pet",
            EntityType.Builder.of(LeshenPet::new, MobCategory.MISC).sized(0.35F, 0.85F).eyeHeight(0.72F).noSave()
                    .clientTrackingRange(10));
    public static final Item LESHEN_SPAWN_EGG = spawnEgg("leshen_spawn_egg", LESHEN);
    public static final Item CORRUPTED_STAG_SPAWN_EGG = spawnEgg("corrupted_stag_spawn_egg", CORRUPTED_STAG);
    public static final Item FALSEKIN_SPAWN_EGG = spawnEgg("falsekin_spawn_egg", FALSEKIN);
    public static final Item VOID_WRAITH_SPAWN_EGG = spawnEgg("void_wraith_spawn_egg", VOID_WRAITH);
    /** The Hollowed Penitent, a world boss: a man the void hollowed out from the face inwards. */
    public static final EntityType<HollowedPenitent> HOLLOWED_PENITENT = register("hollowed_penitent",
            EntityType.Builder.of(HollowedPenitent::new, MobCategory.MONSTER).sized(1.4F, 3.2F).eyeHeight(2.7F)
                    .clientTrackingRange(10));
    public static final Item HOLLOWED_PENITENT_SPAWN_EGG = spawnEgg("hollowed_penitent_spawn_egg", HOLLOWED_PENITENT);

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
        FabricDefaultAttributeRegistry.register(VOID_WRAITH, VoidWraith.createAttributes());
        FabricDefaultAttributeRegistry.register(HOLLOWED_PENITENT, HollowedPenitent.createAttributes());
        VoidWraith.registerPayload();
        VoidWraith.registerStunCrash();
        UnbearableHeat.register();
        FallenStar.register();
        FabricDefaultAttributeRegistry.register(STAG_MOUNT, StagMount.createMountAttributes());
        FabricDefaultAttributeRegistry.register(UNICORN_MOUNT, UnicornMount.createMountAttributes());
        UnicornBoost.register();
        FabricDefaultAttributeRegistry.register(LESHEN_PET, LeshenPet.createPetAttributes());
        ekuzo.crazyworldprogression.collectibles.Collectibles.registerMount(new ekuzo.crazyworldprogression.collectibles.Collectibles.MountDefinition(
                ekuzo.echeloncore.EchelonCore.id("corrupted_stag"), "Corrupted Stag", ekuzo.crazyworldprogression.collectibles.Rarity.EPIC,
                STAG_MOUNT, 0.3, 0.9));
        ekuzo.crazyworldprogression.collectibles.Collectibles.registerMount(new ekuzo.crazyworldprogression.collectibles.Collectibles.MountDefinition(
                ekuzo.echeloncore.EchelonCore.id("unicorn"), "Shainicorn", ekuzo.crazyworldprogression.collectibles.Rarity.LEGENDARY,
                UNICORN_MOUNT, 0.34, 1.0));
        ekuzo.crazyworldprogression.collectibles.Collectibles.registerPet(new ekuzo.crazyworldprogression.collectibles.Collectibles.PetDefinition(
                ekuzo.echeloncore.EchelonCore.id("leshen"), "Little Leshen", ekuzo.crazyworldprogression.collectibles.Rarity.RARE, LESHEN_PET));
        registerMiniPets();
        // Vanilla mobs as mounts (CWP look mounts): speed, jump strength, rider height.
        LookMount.register(EchelonCore.id("zombie_horse_mount"), "Zombie Horse", Rarity.RARE, EntityTypes.ZOMBIE_HORSE, 0.32, 0.9, 1.4F);
        LookMount.register(EchelonCore.id("camel_husk_mount"), "Camel Husk", Rarity.EPIC, EntityTypes.CAMEL_HUSK, 0.27, 0.6, 1.95F);
        LookMount.register(EchelonCore.id("zoglin_mount"), "Zoglin", Rarity.EPIC, EntityTypes.ZOGLIN, 0.31, 0.75, 1.15F);
        ResourceKey<CreativeModeTab> eggs = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                Identifier.withDefaultNamespace("spawn_eggs"));
        CreativeModeTabEvents.modifyOutputEvent(eggs).register(output -> {
            output.accept(LESHEN_SPAWN_EGG);
            output.accept(CORRUPTED_STAG_SPAWN_EGG);
            output.accept(FALSEKIN_SPAWN_EGG);
            output.accept(VOID_WRAITH_SPAWN_EGG);
            output.accept(HOLLOWED_PENITENT_SPAWN_EGG);
        });
    }

    /** Smaller vanilla mobs as unlockable pets (CWP mini pets): name, rarity, mob, size, and whether it hovers. */
    private static void registerMiniPets() {
        miniPet("fox", "Mini Fox", Rarity.UNCOMMON, EntityTypes.FOX, 0.55F, false);
        miniPet("allay", "Mini Allay", Rarity.RARE, EntityTypes.ALLAY, 0.75F, true);
        miniPet("frog", "Mini Frog", Rarity.UNCOMMON, EntityTypes.FROG, 0.6F, false);
        miniPet("bee", "Mini Bee", Rarity.UNCOMMON, EntityTypes.BEE, 0.7F, true);
        miniPet("copper_golem", "Mini Copper Golem", Rarity.RARE, EntityTypes.COPPER_GOLEM, 0.6F, false);
        miniPet("parrot", "Mini Parrot", Rarity.UNCOMMON, EntityTypes.PARROT, 0.7F, true);
        miniPet("iron_golem", "Mini Iron Golem", Rarity.EPIC, EntityTypes.IRON_GOLEM, 0.3F, false);
        miniPet("rabbit", "Mini Rabbit", Rarity.COMMON, EntityTypes.RABBIT, 0.65F, false);
        miniPet("cat", "Mini Cat", Rarity.UNCOMMON, EntityTypes.CAT, 0.6F, false);
        miniPet("bat", "Mini Bat", Rarity.COMMON, EntityTypes.BAT, 0.7F, true);
        miniPet("slime", "Mini Slime", Rarity.COMMON, EntityTypes.SLIME, 0.6F, false);
        miniPet("armadillo", "Mini Armadillo", Rarity.UNCOMMON, EntityTypes.ARMADILLO, 0.6F, false);
        miniPet("ghast", "Mini Ghast", Rarity.LEGENDARY, EntityTypes.GHAST, 0.07F, true);
        miniPet("zombie", "Mini Zombie", Rarity.RARE, EntityTypes.ZOMBIE, 0.4F, false);
        miniPet("hoglin", "Mini Hoglin", Rarity.EPIC, EntityTypes.HOGLIN, 0.35F, false);
        miniPet("blaze", "Mini Blaze", Rarity.RARE, EntityTypes.BLAZE, 0.45F, true);
        miniPet("spider", "Mini Spider", Rarity.UNCOMMON, EntityTypes.SPIDER, 0.45F, false);
        miniPet("panda", "Baby Panda", Rarity.RARE, MiniPet.Look.of(EntityTypes.PANDA, 0.8F, false).asBaby());
        miniPet("creaking", "Mini Creaking", Rarity.EPIC, EntityTypes.CREAKING, 0.35F, false);
        // A tropical fish swimming in a water bubble (art/pet_bubble.bbmodel, item model echelon-core:pet_bubble).
        miniPet("void_wraith", "Mini Void Wraith", Rarity.LEGENDARY, VOID_WRAITH, 0.4F, true);
        miniPet("bubble_fish", "Bubble Fish", Rarity.RARE, MiniPet.Look.of(EntityTypes.TROPICAL_FISH, 0.8F, true)
                .inShell(EchelonCore.id("pet_bubble"), 0.7F));
    }

    private static void miniPet(String name, String title, Rarity rarity, EntityType<?> vanilla, float scale, boolean hovers) {
        miniPet(name, title, rarity, MiniPet.Look.of(vanilla, scale, hovers));
    }

    private static void miniPet(String name, String title, Rarity rarity, MiniPet.Look look) {
        MiniPet.register(EchelonCore.id("mini_" + name), title, rarity, look);
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
