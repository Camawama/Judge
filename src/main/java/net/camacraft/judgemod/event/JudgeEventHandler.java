package net.camacraft.judgemod.event;

import net.camacraft.judgemod.JudgeMod;
import net.camacraft.judgemod.capability.IJudgeData;
import net.camacraft.judgemod.capability.JudgeDataProvider;
import net.camacraft.judgemod.data.JudgeDataManager;
import net.camacraft.judgemod.mixin.ZombieVillagerAccessor;
import net.camacraft.judgemod.network.PacketHandler;
import net.camacraft.judgemod.network.PacketJudgePopup;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.FrameType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public class JudgeEventHandler {
    private static final String SPAWNER_TAG = "FromSpawner";
    private static final int GATE_MESSAGE_THROTTLE_TICKS = 20;

    /** Throttles gate-denial messages so portal/holding-right-click spam doesn't flood the action bar. */
    private static final Map<UUID, Long> LAST_GATE_MESSAGE = new HashMap<>();

    /**
     * True while {@link #applyThresholdEffects} is adding its effects. Threshold-granted
     * Hero of the Village must not be mistaken for a raid victory in {@link #onEffectAdded}.
     */
    private static boolean applyingThresholdEffects = false;

    /** Players outside normal survival judgment: creative, spectator, and mod-machinery fake players. */
    private static boolean isExempt(Player player) {
        return player.isCreative() || player.isSpectator() || player instanceof FakePlayer;
    }

    @SubscribeEvent
    public void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player && !event.getObject().getCapability(JudgeDataProvider.JUDGE_DATA).isPresent()) {
            event.addCapability(new ResourceLocation(JudgeMod.MODID, "judge_data"), new JudgeDataProvider());
        }
    }

    @SubscribeEvent
    public void onMobSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (event.getSpawnType() == MobSpawnType.SPAWNER) {
            event.getEntity().addTag(SPAWNER_TAG);
        }
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            long now = event.getEntity().level().getGameTime();
            event.getOriginal().reviveCaps(); // Required to access capabilities of dead player
            event.getOriginal().getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(oldData -> {
                oldData.recordDeath(now);

                event.getEntity().getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(newData -> {
                    newData.copyFrom(oldData);
                    newData.resetForNewLife();
                    newData.setLastDeathLifeScore(oldData.getLifeScore());

                    JudgeDataManager.FracturedComposureConfig fcConfig = JudgeDataManager.getFcConfig();
                    long windowTicks = fcConfig.windowMinutes * 60L * 20L;
                    int deaths = oldData.getDeathsInWindow(windowTicks, now);
                    int stacks = deaths >= fcConfig.triggerDeaths ? Math.min(fcConfig.maxStacks, deaths - (fcConfig.triggerDeaths - 1)) : 0;
                    newData.setFracturedComposureStacks(Math.max(oldData.getFracturedComposureStacks(), stacks));
                });
            });
            event.getOriginal().invalidateCaps(); // Re-invalidate after use
        } else {
            event.getOriginal().getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(oldData ->
                    event.getEntity().getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(newData -> newData.deserializeNBT(oldData.serializeNBT())));
        }
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            markCurrentDimensionEntered(player);
            PacketHandler.syncJudgeData(player);
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            markCurrentDimensionEntered(player);
            PacketHandler.syncJudgeData(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_GATE_MESSAGE.remove(event.getEntity().getUUID());
    }

    /**
     * The dimension a player is already in never counts as "newly entered", so a
     * Nether round trip can't pay the enter_dimension reward twice (once per side).
     */
    private void markCurrentDimensionEntered(ServerPlayer player) {
        player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data ->
                data.setEnteredDimensionThisLife(player.level().dimension().location().toString()));
    }

    /** Gate check only. The entry reward is granted after travel completes, in {@link #onPlayerChangedDimension}. */
    @SubscribeEvent
    public void onDimensionTravel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || isExempt(player)) return;
        ResourceLocation dim = event.getDimension().location();
        int req = JudgeDataManager.getDimensionReq(dim.toString());
        if (req <= 0) return;
        player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data -> {
            if (data.getJudgeLevel() < req) {
                event.setCanceled(true);
                sendGateMessage(player, Component.translatable("message.judgemod.gate_dimension", req).withStyle(ChatFormatting.RED));
            }
        });
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || isExempt(player)) return;
        String dim = event.getTo().location().toString();
        player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data -> {
            if (!data.hasEnteredDimensionThisLife(dim)) {
                applyRule(player, data, "enter_dimension", null);
                data.setEnteredDimensionThisLife(dim);
            }
        });
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        // Victim handling and killer credit are independent: a player killing
        // another player must both sync the victim and score the killer.
        if (event.getEntity() instanceof ServerPlayer victim) {
            victim.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data -> {
                victim.setScore(data.getLifeScore());
                data.setLastDeathLifeScore(data.getLifeScore());
                PacketHandler.syncJudgeData(victim);
            });
        }
        if (event.getSource().getEntity() instanceof ServerPlayer killer
                && killer != event.getEntity() && !isExempt(killer)) {
            applyRule(killer, "kill_mob", event.getEntity());
        }
    }

    @SubscribeEvent
    public void onEffectAdded(MobEffectEvent.Added event) {
        if (applyingThresholdEffects) return; // threshold rewards are not raid victories
        if (!(event.getEntity() instanceof ServerPlayer player) || isExempt(player)) return;

        MobEffectInstance inst = event.getEffectInstance();
        if (inst != null && inst.getEffect() == MobEffects.HERO_OF_THE_VILLAGE) {
            MobEffectInstance old = event.getOldEffectInstance();
            if (old != null && old.getEffect() == MobEffects.HERO_OF_THE_VILLAGE) return;
            applyRule(player, "win_raid", null);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (player instanceof FakePlayer) return;

        player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data -> {
            // Always tick the clock so composure decay isn't frozen by creative/spectator stints
            data.tick();
            if (player.getScore() != data.getLifeScore()) player.setScore(data.getLifeScore());

            if (data.getTimeSinceLastDeath() > 0) {
                if (!isExempt(player) && data.getTimeSinceLastDeath() % JudgeDataManager.getSurviveIntervalTicks() == 0) {
                    applyRule(player, data, "survive_time", null);
                }

                JudgeDataManager.FracturedComposureConfig fcConfig = JudgeDataManager.getFcConfig();
                int decayTicks = fcConfig.decayMinutes * 60 * 20; // decayMinutes is clamped >= 1 at parse
                if (data.getFracturedComposureStacks() > 0 && data.getTimeSinceLastDeath() % decayTicks == 0) {
                    data.setFracturedComposureStacks(data.getFracturedComposureStacks() - 1);
                    player.sendSystemMessage(Component.translatable("message.judgemod.composure_recover").withStyle(ChatFormatting.GREEN));
                    PacketHandler.syncJudgeData(player);
                }
            }

            // Run less frequently (every second) for performance-heavy checks
            if (!isExempt(player) && player.tickCount % 20 == 0) {
                applyThresholdEffects(player, data);
            }
        });
    }

    @SubscribeEvent
    public void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || isExempt(player)) return;
        String type = event.getSource().getMsgId();
        JudgeDataManager.getMistakeRules().stream().filter(r -> r.damageType.equals(type)).findFirst().ifPresent(rule ->
                player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data -> {
                    long now = player.level().getGameTime();
                    List<Long> timestamps = data.getActionTimestamps("mistake_" + type);
                    if (rule.cooldownSeconds > 0 && !timestamps.isEmpty()
                            && (now - timestamps.get(timestamps.size() - 1)) < rule.cooldownSeconds * 20L) return;

                    int penalty = rule.basePenalty + (int) (event.getAmount() / 2.0f * rule.perHeartPenalty);
                    int stacks = data.getFracturedComposureStacks();
                    double multiplier = 1.0 + (stacks * JudgeDataManager.getFcConfig().mistakeMultiplier);
                    int finalPenalty = (int) (penalty * multiplier);

                    data.recordAction("mistake_" + type, now, -finalPenalty);
                    data.addLifeScore(-finalPenalty);
                    player.setScore(data.getLifeScore()); // Sync vanilla score immediately
                    PacketHandler.sendToPlayer(new PacketJudgePopup("mistake_" + type, -finalPenalty, 1, false), player);
                    PacketHandler.syncJudgeData(player);
                }));
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !isExempt(player)) {
            applyRule(player, "hit_entity", event.getTarget());
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || isExempt(player)) return;
        player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data -> {
            String blockId = String.valueOf(ForgeRegistries.BLOCKS.getKey(event.getLevel().getBlockState(event.getPos()).getBlock()));
            int blockReq = JudgeDataManager.getBlockInteractReq(blockId);
            if (blockReq > 0 && data.getJudgeLevel() < blockReq) {
                event.setCanceled(true);
                sendGateMessage(player, Component.translatable("message.judgemod.gate_block", blockReq).withStyle(ChatFormatting.RED));
                return;
            }

            // Items used ON blocks (minecarts, end crystals, fireworks, ...) never reach
            // RightClickItem, so the item gate must also deny the use-item path here.
            int itemReq = gatedItemRequirement(data, event.getItemStack());
            if (itemReq > 0) {
                event.setUseItem(Event.Result.DENY);
                sendGateMessage(player, Component.translatable("message.judgemod.gate_item", itemReq).withStyle(ChatFormatting.RED));
                player.inventoryMenu.sendAllDataToRemote();
            }
        });
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || isExempt(player)) return;
        player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data -> {
            int itemReq = gatedItemRequirement(data, event.getItemStack());
            if (itemReq > 0) {
                event.setCanceled(true);
                sendGateMessage(player, Component.translatable("message.judgemod.gate_item", itemReq).withStyle(ChatFormatting.RED));
                player.inventoryMenu.sendAllDataToRemote();
            }
        });
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        checkEntityInteractItemGate(event);
    }

    @SubscribeEvent
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        checkEntityInteractItemGate(event);
    }

    /** Leads, name tags, etc. are used on entities and bypass both right-click item paths. */
    private void checkEntityInteractItemGate(PlayerInteractEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || isExempt(player)) return;
        player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data -> {
            int itemReq = gatedItemRequirement(data, event.getItemStack());
            if (itemReq > 0) {
                event.setCanceled(true);
                sendGateMessage(player, Component.translatable("message.judgemod.gate_item", itemReq).withStyle(ChatFormatting.RED));
                player.inventoryMenu.sendAllDataToRemote();
            }
        });
    }

    /** Returns the required Judge Level if the stack is gated above the player's level, otherwise 0. */
    private int gatedItemRequirement(IJudgeData data, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        String itemId = String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()));
        int req = JudgeDataManager.getItemUseReq(itemId);
        return (req > 0 && data.getJudgeLevel() < req) ? req : 0;
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || isExempt(player)) return;
        String blockId = String.valueOf(ForgeRegistries.BLOCKS.getKey(event.getState().getBlock()));
        int req = JudgeDataManager.getBlockBreakReq(blockId);
        if (req > 0) {
            IJudgeData data = player.getCapability(JudgeDataProvider.JUDGE_DATA).orElse(null);
            if (data != null && data.getJudgeLevel() < req) {
                event.setCanceled(true);
                sendGateMessage(player, Component.translatable("message.judgemod.gate_break", req).withStyle(ChatFormatting.RED));
                return;
            }
        }
        player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data ->
                applyBlockRule(player, data, "break_block", event.getState()));
    }

    @SubscribeEvent
    public void onPlayerSleep(PlayerWakeUpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level().isDay()) {
            if (isExempt(player)) return;
            long day = player.level().getDayTime() / 24000L;
            player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data -> {
                if (!data.hasSleptOnDay(day)) {
                    applyRule(player, data, "sleep", null);
                    data.setLastSleptDay(day);
                }
            });
        }
    }

    @SubscribeEvent
    public void onEntityConversion(LivingConversionEvent.Post event) {
        if (event.getEntity() instanceof ZombieVillager zombieVillager && event.getOutcome() instanceof Villager
                && zombieVillager.level() instanceof ServerLevel serverLevel) {
            // Credit the player who actually started the cure; fall back to proximity.
            ServerPlayer credited = null;
            UUID starter = ((ZombieVillagerAccessor) zombieVillager).judgemod$getConversionStarter();
            if (starter != null) {
                ServerPlayer p = serverLevel.getServer().getPlayerList().getPlayer(starter);
                if (p != null && !isExempt(p)) credited = p;
            }
            if (credited == null) {
                credited = serverLevel.getEntitiesOfClass(ServerPlayer.class,
                                zombieVillager.getBoundingBox().inflate(16.0), p -> !isExempt(p))
                        .stream().findFirst().orElse(null);
            }
            if (credited != null) applyRule(credited, "cure_villager", null);
        }
    }

    @SubscribeEvent
    public void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !isExempt(player)) {
            DisplayInfo display = event.getAdvancement().getDisplay();
            if (display != null) {
                if (display.getFrame() == FrameType.CHALLENGE) applyRule(player, "advancement_challenge", null);
                else if (display.getFrame() == FrameType.GOAL) applyRule(player, "advancement_goal", null);
                else if (display.getFrame() == FrameType.TASK) applyRule(player, "advancement_task", null);
            }
        }
    }

    @SubscribeEvent
    public void onTrade(TradeWithVillagerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !isExempt(player)) {
            applyRule(player, "trade_villager", null);
        }
    }

    @SubscribeEvent
    public void onLootContainerOpen(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player) || isExempt(player)) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        // Only naturally generated, still-unopened loot containers count: they carry a
        // pending LootTable tag that vanilla clears on first open. Player-placed chests
        // never have one, so opening your own storage in a village is not "looting".
        if (!(serverLevel.getBlockEntity(event.getPos()) instanceof RandomizableContainerBlockEntity container)) return;
        if (!container.saveWithoutMetadata().contains("LootTable", Tag.TAG_STRING)) return;

        player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data -> {
            if (serverLevel.isVillage(event.getPos())) {
                applyRule(player, data, "loot_village_chest", null);
            } else {
                applyRule(player, data, "loot_chest", null);
            }
        });
    }

    private void sendGateMessage(ServerPlayer player, Component message) {
        long now = player.level().getGameTime();
        Long last = LAST_GATE_MESSAGE.get(player.getUUID());
        if (last != null && now - last < GATE_MESSAGE_THROTTLE_TICKS) return;
        LAST_GATE_MESSAGE.put(player.getUUID(), now);
        player.displayClientMessage(message, true);
    }

    private void applyRule(ServerPlayer player, String triggerType, Entity target) {
        player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data -> applyRule(player, data, triggerType, target));
    }

    private void applyRule(ServerPlayer player, IJudgeData data, String triggerType, Entity target) {
        processRules(player, data, triggerType, rule -> matchesEntityTarget(rule, target), target);
    }

    private void applyBlockRule(ServerPlayer player, IJudgeData data, String triggerType, BlockState state) {
        processRules(player, data, triggerType, rule -> matchesBlockTarget(rule, state), null);
    }

    private void processRules(ServerPlayer player, IJudgeData data, String triggerType,
                              Predicate<JudgeDataManager.ActionRule> targetMatcher, Entity target) {
        long now = player.level().getGameTime();
        for (JudgeDataManager.ActionRule rule : JudgeDataManager.getActionRules()) {
            if (!rule.trigger.equals(triggerType)) continue;
            if (!targetMatcher.test(rule)) continue;
            if (rule.ignoreSpawners && target != null && target.getTags().contains(SPAWNER_TAG)) continue;
            if (!checkCondition(rule, player, target)) continue;

            if (rule.cooldownSeconds > 0) {
                List<Long> timestamps = data.getActionTimestamps(rule.id);
                if (!timestamps.isEmpty() && (now - timestamps.get(timestamps.size() - 1)) < rule.cooldownSeconds * 20L) continue;
            }
            if (rule.cap > 0 && rule.capDurationMinutes > 0) {
                long cutoff = now - rule.capDurationMinutes * 60L * 20L;
                if (data.getActionTimestamps(rule.id).stream().filter(t -> t > cutoff).count() >= rule.cap) continue;
            }

            int combo = 1;
            if (rule.combo_window_seconds > 0) {
                if (rule.id.equals(data.getComboActionKey()) && now < data.getComboExpireTime()) {
                    combo = data.getComboCount() + 1;
                }
                data.setComboActionKey(rule.id);
                data.setComboCount(combo);
                data.setComboExpireTime(now + rule.combo_window_seconds * 20L);
            }

            int delta = rule.delta;
            if (combo > 1) {
                if (delta > 0) delta += (combo - 1); // Linear positive combo
                else delta -= (combo - 1) * (combo - 1); // Quadratic negative combo
            }

            if (delta < 0) {
                int stacks = data.getFracturedComposureStacks();
                double multiplier = 1.0 + (stacks * JudgeDataManager.getFcConfig().dishonorMultiplier);
                delta = (int) (delta * multiplier);
            }

            data.recordAction(rule.id, now, delta);
            data.addLifeScore(delta);
            player.setScore(data.getLifeScore()); // Sync vanilla score immediately
            PacketHandler.sendToPlayer(new PacketJudgePopup(rule.id, delta, combo, false), player);
            PacketHandler.syncJudgeData(player);

            if (rule.exclusive) break;
        }
    }

    private boolean matchesEntityTarget(JudgeDataManager.ActionRule rule, Entity target) {
        if (rule.target == null || rule.target.equals("any") || target == null) return true;
        if (rule.target.startsWith("#")) {
            TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(rule.target.substring(1)));
            if (target.getType().is(tag)) return true;
            // Class-based fallback so modded entities absent from the tags still count.
            // Hoglins are both Animal and Enemy; they count as monsters, not passives.
            if ((rule.target.equals("#judgemod:animals") || rule.target.equals("#minecraft:animals"))
                    && target instanceof Animal && !(target instanceof Enemy)) return true;
            if ((rule.target.equals("#judgemod:monsters") || rule.target.equals("#minecraft:monsters"))
                    && target instanceof Enemy) return true;
            return false;
        }
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        return key != null && key.toString().equals(rule.target);
    }

    private boolean matchesBlockTarget(JudgeDataManager.ActionRule rule, BlockState state) {
        if (rule.target == null || rule.target.equals("any")) return true;
        if (rule.target.startsWith("#")) {
            return state.is(BlockTags.create(new ResourceLocation(rule.target.substring(1))));
        }
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return key != null && key.toString().equals(rule.target);
    }

    private boolean checkCondition(JudgeDataManager.ActionRule rule, ServerPlayer player, Entity target) {
        if (rule.condition == null) return true;
        return switch (rule.condition) {
            case "player_food_full" -> player.getFoodData().getFoodLevel() >= 20;
            case "is_tamed" -> target instanceof TamableAnimal tamable && tamable.isTame();
            // Uses the player's position; they stand within interaction range of the block.
            case "in_village" -> player.serverLevel().isVillage(player.blockPosition());
            default -> true;
        };
    }

    private void applyThresholdEffects(ServerPlayer player, IJudgeData data) {
        int score = data.getLifeScore();
        // Only the strongest qualifying tier per effect applies.
        Map<String, JudgeDataManager.ThresholdTier> best = new HashMap<>();
        for (JudgeDataManager.ThresholdTier tier : JudgeDataManager.getThresholdTiers()) {
            boolean applies = tier.isNegative ? score <= tier.threshold : score >= tier.threshold;
            if (!applies) continue;
            JudgeDataManager.ThresholdTier current = best.get(tier.effectId);
            if (current == null || tier.amplifier > current.amplifier) best.put(tier.effectId, tier);
        }
        applyingThresholdEffects = true;
        try {
            for (JudgeDataManager.ThresholdTier tier : best.values()) {
                MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(tier.effectId));
                if (effect != null) player.addEffect(new MobEffectInstance(effect, 10 * 20, tier.amplifier, true, false));
            }
        } finally {
            applyingThresholdEffects = false;
        }
    }
}
