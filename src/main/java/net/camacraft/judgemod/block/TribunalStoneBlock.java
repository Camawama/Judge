package net.camacraft.judgemod.block;

import net.camacraft.judgemod.capability.IJudgeData;
import net.camacraft.judgemod.capability.JudgeDataProvider;
import net.camacraft.judgemod.data.JudgeDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
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
        if (!pLevel.isClientSide() && pPlayer instanceof ServerPlayer serverPlayer) {
            BlockEntity entity = pLevel.getBlockEntity(pPos);
            if (entity instanceof TribunalStoneBlockEntity be) {
                NetworkHooks.openScreen(serverPlayer, be, buf -> {
                    buf.writeBlockPos(pPos);
                    // Always write the full payload; the client decoder reads a fixed
                    // layout and must never underflow if the capability is absent.
                    IJudgeData data = serverPlayer.getCapability(JudgeDataProvider.JUDGE_DATA).orElse(null);
                    int score = data != null ? data.getLifeScore() : 0;
                    int level = data != null ? data.getJudgeLevel() : 0;
                    int stacks = data != null ? data.getFracturedComposureStacks() : 0;
                    buf.writeInt(score);
                    buf.writeInt(level);
                    buf.writeInt(stacks);
                    buf.writeInt(JudgeDataManager.getLevelCost(level));
                });
            }
        }
        return InteractionResult.sidedSuccess(pLevel.isClientSide());
    }
}
