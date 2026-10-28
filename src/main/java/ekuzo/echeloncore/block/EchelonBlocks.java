package ekuzo.echeloncore.block;

import ekuzo.crazyworldprogression.divine.DivineProtection;
import ekuzo.crazyworldprogression.reforge.ReforgeData;
import ekuzo.echeloncore.EchelonCore;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.List;

/**
 * Echelon's blocks. The Divine Altar is where items get Divine Protection; the charges' Soulweaving levels, failure chances and costs
 * (Powerful Souls plus materials, rising per charge) are set here.
 */
public final class EchelonBlocks {
    public static final Block DIVINE_ALTAR = register("divine_altar", DivineAltarBlock::new, BlockBehaviour.Properties.of()
            .mapColor(MapColor.QUARTZ).strength(5.0F, 1200.0F).requiresCorrectToolForDrops().sound(SoundType.STONE)
            .lightLevel(state -> 10).noOcclusion());

    private EchelonBlocks() { }

    public static void register() {
        ResourceKey<CreativeModeTab> functional = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                Identifier.withDefaultNamespace("functional_blocks"));
        CreativeModeTabEvents.modifyOutputEvent(functional).register(output -> output.insertAfter(Items.ENCHANTING_TABLE,
                DIVINE_ALTAR));
        DivineProtection.setCharges(List.of(
                // One charge per Soulweaving rank, Novice (1) to Master (5). Failure chance at the required rank; each rank
                // above it takes off 5 percentage points, each Soulweaving level 2.
                new DivineProtection.Charge(1, List.of(DivineProtection.item("gold_ingot", 8), souls(5)), 25, 2500),
                new DivineProtection.Charge(2, List.of(DivineProtection.item("diamond", 2), souls(15)), 50, 3000),
                new DivineProtection.Charge(3, List.of(DivineProtection.item("diamond", 6), souls(40)), 100, 3500),
                new DivineProtection.Charge(4, List.of(DivineProtection.item("netherite_ingot", 1), souls(100)), 200, 4000),
                new DivineProtection.Charge(5, List.of(DivineProtection.item("nether_star", 1), souls(250)), 400, 5000)));
    }

    private static ReforgeData.Cost souls(int amount) {
        return new ReforgeData.Cost("currency", EchelonCore.POWERFUL_SOULS, amount);
    }

    private static Block register(String name, java.util.function.Function<BlockBehaviour.Properties, Block> factory,
                                  BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, EchelonCore.id(name));
        Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, EchelonCore.id(name));
        Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey)
                .useBlockDescriptionPrefix()));
        return block;
    }
}
