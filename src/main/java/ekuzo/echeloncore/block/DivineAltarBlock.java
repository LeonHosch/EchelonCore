package ekuzo.echeloncore.block;

import com.mojang.serialization.MapCodec;
import ekuzo.crazyworldprogression.divine.DivineProtectionNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The Divine Altar: right-click opens CWP's Divine Protection menu, where items are blessed with charges. */
public final class DivineAltarBlock extends Block {
    public static final MapCodec<DivineAltarBlock> CODEC = simpleCodec(DivineAltarBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 3, 15), Block.box(4, 3, 4, 12, 10, 12),
            Block.box(2, 10, 2, 14, 13, 14));

    public DivineAltarBlock(Properties properties) { super(properties); }

    @Override
    protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer)
            DivineProtectionNetworking.open(serverPlayer, pos, current -> current.is(this));
        return InteractionResult.SUCCESS;
    }
}
