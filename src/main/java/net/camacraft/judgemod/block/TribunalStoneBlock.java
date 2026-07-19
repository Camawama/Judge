package net.camacraft.judgemod.block;

import net.camacraft.judgemod.capability.JudgeDataProvider;
import net.camacraft.judgemod.data.JudgeDataManager;
import net.camacraft.judgemod.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

public class TribunalStoneBlock extends BaseEntityBlock {
    public TribunalStoneBlock(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new TribunalStoneBlockEntity(pPos, pState);
    }

    @Override
    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        if (!pLevel.isClientSide()) {
            BlockEntity entity = pLevel.getBlockEntity(pPos);
            if (entity instanceof TribunalStoneBlockEntity be) {
                NetworkHooks.openScreen((ServerPlayer) pPlayer, be, buf -> {
                    buf.writeBlockPos(pPos);
                    pPlayer.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data -> {
                        buf.writeInt(data.getLifeScore());
                        buf.writeInt(data.getJudgeLevel());
                        buf.writeInt(data.getFracturedComposureStacks());

                        JudgeDataManager.LevelCostConfig costConfig = JudgeDataManager.getLevelCostConfig();
                        int currentLevel = data.getJudgeLevel();
                        int cost = costConfig.B + costConfig.A * currentLevel + costConfig.Q * currentLevel * currentLevel;
                        buf.writeInt(cost);
                    });
                });
            } else {
                throw new IllegalStateException("Our Container provider is missing!");
            }
        }
        return InteractionResult.sidedSuccess(pLevel.isClientSide());
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level pLevel, BlockState pState, BlockEntityType<T> pBlockEntityType) {
        return createTickerHelper(pBlockEntityType, ModBlockEntities.TRIBUNAL_STONE_BE.get(), TribunalStoneBlockEntity::tick);
    }
}
