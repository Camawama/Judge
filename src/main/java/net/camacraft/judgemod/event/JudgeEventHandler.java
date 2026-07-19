package net.camacraft.judgemod.event;

import net.camacraft.judgemod.JudgeMod;
import net.camacraft.judgemod.capability.IJudgeData;
import net.camacraft.judgemod.capability.JudgeDataProvider;
import net.camacraft.judgemod.data.JudgeDataManager;
import net.camacraft.judgemod.network.PacketHandler;
import net.camacraft.judgemod.network.PacketJudgePopup;
import net.camacraft.judgemod.network.PacketSyncJudgeData;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.FrameType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class JudgeEventHandler {

    @SubscribeEvent
    public void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player && !event.getObject().getCapability(JudgeDataProvider.JUDGE_DATA).isPresent()) {
            event.addCapability(new ResourceLocation(JudgeMod.MODID, "judge_data"), new JudgeDataProvider());
        }
    }

    @SubscribeEvent
    public void onMobSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (event.getSpawnType() == MobSpawnType.SPAWNER) {
            event.getEntity().addTag("FromSpawner");
        }
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            event.getOriginal().reviveCaps(); // Required to access capabilities of dead player
            event.getOriginal().getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(oldData -> {
                // Fix: Record death on old data BEFORE copying to new data
                oldData.recordDeath();
                
                event.getEntity().getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(newData -> {
                    newData.copyFrom(oldData);
                    newData.resetForNewLife();
                    newData.setLastDeathLifeScore(oldData.getLifeScore());
                    // Explicitly ensure level is preserved, though copyFrom should handle it
                    newData.setJudgeLevel(oldData.getJudgeLevel());
                    
                    JudgeDataManager.FracturedComposureConfig fcConfig = JudgeDataManager.getFcConfig();
                    int deaths = oldData.getDeathsInWindow(TimeUnit.MINUTES.toMillis(fcConfig.windowMinutes));
                    int stacks = deaths >= fcConfig.triggerDeaths ? Math.min(fcConfig.maxStacks, deaths - (fcConfig.triggerDeaths - 1)) : 0;
                    newData.setFracturedComposureStacks(Math.max(oldData.getFracturedComposureStacks(), stacks));
                });
            });
            event.getOriginal().invalidateCaps(); // Re-invalidate after use
        } else {
            event.getOriginal().getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(oldData -> {
                event.getEntity().getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(newData -> newData.deserializeNBT(oldData.serializeNBT()));
            });
        }
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) syncData(player);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) syncData(player);
    }

    @SubscribeEvent
    public void onDimensionTravel(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.isCreative()) return;
            ResourceLocation dim = event.getDimension().location();
            int req = JudgeDataManager.getDimensionReq(dim.toString());
            player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data -> {
                if (data.getJudgeLevel() < req) {
                    event.setCanceled(true);
                    player.displayClientMessage(Component.literal("Judge Level " + req + " required.").withStyle(ChatFormatting.RED), true);
                } else if (!data.hasEnteredDimensionThisLife(dim.toString())) {
                    applyRule(player, data, "enter_dimension", null);
                    data.setEnteredDimensionThisLife(dim.toString());
                }
            });
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data -> {
                player.setScore(data.getLifeScore());
                data.setLastDeathLifeScore(data.getLifeScore()); // Update capability so syncData sends correct value
                PacketHandler.sendToPlayer(new PacketSyncJudgeData(data.getLifeScore(), data.getJudgeLevel(), data.getLastDeathLifeScore(), data.getFracturedComposureStacks()), player);
            });
        } else if (event.getSource().getEntity() instanceof ServerPlayer player) {
            if (player.isCreative()) return;
            LivingEntity target = event.getEntity();

            applyRule(player, "kill_mob", target);
        }
    }

    @SubscribeEvent
    public void onEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;

        MobEffectInstance inst = event.getEffectInstance();
        if (inst != null && inst.getEffect() == MobEffects.HERO_OF_THE_VILLAGE) {
            // Fix: Check if the player already had the effect to prevent spamming
            MobEffectInstance old = event.getOldEffectInstance();
            if (old != null && old.getEffect() == MobEffects.HERO_OF_THE_VILLAGE) return;

            applyRule(player, "win_raid", null);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            if (player.isCreative()) return;
            
            player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data -> {
                // Run every tick for accurate timing and strict score sync
                data.tick();
                if (player.getScore() != data.getLifeScore()) player.setScore(data.getLifeScore());

                if (data.getTimeSinceLastDeath() > 0) {
                    if (data.getTimeSinceLastDeath() % (10 * 60 * 20) == 0) {
                        applyRule(player, data, "survive_time", null);
                    }
                    
                    JudgeDataManager.FracturedComposureConfig fcConfig = JudgeDataManager.getFcConfig();
                    if (data.getFracturedComposureStacks() > 0 && data.getTimeSinceLastDeath() % (fcConfig.decayMinutes * 60 * 20) == 0) {
                        data.setFracturedComposureStacks(data.getFracturedComposureStacks() - 1);
                        player.sendSystemMessage(Component.literal("Your composure recovers slightly...").withStyle(ChatFormatting.GREEN));
                        syncData(player);
                    }
                }

                // Run less frequently (every second) for performance-heavy checks
                if (player.tickCount % 20 == 0) {
                    applyThresholdEffects(player, data);
                }
            });
        }
    }

    @SubscribeEvent
    public void onDamage(LivingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.isCreative()) return;
            String type = event.getSource().getMsgId();
            JudgeDataManager.getMistakeRules().stream().filter(r -> r.damageType.equals(type)).findFirst().ifPresent(rule -> {
                player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data -> {
                    long now = System.currentTimeMillis();
                    List<Long> timestamps = data.getActionTimestamps("mistake_" + type);
                    if (rule.cooldownSeconds > 0 && !timestamps.isEmpty() && (now - timestamps.get(timestamps.size() - 1)) < rule.cooldownSeconds * 1000) return;
                    
                    int penalty = rule.basePenalty + (int) (event.getAmount() / 2.0f * rule.perHeartPenalty);
                    int stacks = data.getFracturedComposureStacks();
                    double multiplier = 1.0 + (stacks * JudgeDataManager.getFcConfig().mistakeMultiplier);
                    int finalPenalty = (int) (penalty * multiplier);
                    
                    data.recordAction("mistake_" + type, now, -finalPenalty);
                    data.addLifeScore(-finalPenalty);
                    player.setScore(data.getLifeScore()); // Sync vanilla score immediately
                    PacketHandler.sendToPlayer(new PacketJudgePopup("mistake_" + type, -finalPenalty, 1), player);
                    syncData(player);
                });
            });
        }
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof Villager) {
            if (player.isCreative()) return;
            applyRule(player, "hit_villager", null);
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.isCreative()) return;
            String blockId = ForgeRegistries.BLOCKS.getKey(event.getLevel().getBlockState(event.getPos()).getBlock()).toString();
            int req = JudgeDataManager.getBlockInteractReq(blockId);
            if (req > 0) {
                player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data -> {
                    if (data.getJudgeLevel() < req) {
                        event.setCanceled(true);
                        player.displayClientMessage(Component.literal("Judge Level " + req + " required.").withStyle(ChatFormatting.RED), true);
                    }
                });
            }
        }
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.isCreative()) return;
            String itemId = ForgeRegistries.ITEMS.getKey(event.getItemStack().getItem()).toString();
            int req = JudgeDataManager.getItemUseReq(itemId);
            if (req > 0) {
                player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data -> {
                    if (data.getJudgeLevel() < req) {
                        event.setCanceled(true);
                        player.displayClientMessage(Component.literal("Judge Level " + req + " required to use this.").withStyle(ChatFormatting.RED), true);
                        player.inventoryMenu.sendAllDataToRemote();
                    }
                });
            }
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            if (player.isCreative()) return;
            String blockId = ForgeRegistries.BLOCKS.getKey(event.getState().getBlock()).toString();
            int req = JudgeDataManager.getBlockBreakReq(blockId);
            if (req > 0) {
                player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data -> {
                    if (data.getJudgeLevel() < req) {
                        event.setCanceled(true);
                        player.displayClientMessage(Component.literal("Judge Level " + req + " required to break this.").withStyle(ChatFormatting.RED), true);
                    }
                });
            }
            if (event.getLevel() instanceof ServerLevel serverLevel && serverLevel.isVillage(event.getPos()) && event.getState().is(BlockTags.create(new ResourceLocation("minecraft", "job_site_blocks")))) {
                applyRule(player, "break_workstation", null);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerSleep(PlayerWakeUpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level().isDay()) {
            if (player.isCreative()) return;
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
        if (event.getEntity() instanceof ZombieVillager && event.getOutcome() instanceof Villager) {
            event.getEntity().level().getEntitiesOfClass(ServerPlayer.class, event.getEntity().getBoundingBox().inflate(16.0), p -> !p.isCreative()).stream().findFirst().ifPresent(player -> applyRule(player, "cure_villager", null));
        }
    }

    @SubscribeEvent
    public void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.isCreative()) return;
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
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.isCreative()) return;
            applyRule(player, "trade_villager", null);
        }
    }

    @SubscribeEvent
    public void onChestOpen(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().getBlockState(event.getPos()).is(ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft", "chest")))) {
            if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel serverLevel) {
                if (player.isCreative()) return;
                player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data -> {
                    if (!data.hasLootedChest(event.getPos())) {
                        if (serverLevel.isVillage(event.getPos())) {
                            applyRule(player, data, "loot_village_chest", null);
                            data.addLootedChest(event.getPos());
                        } else {
                            applyRule(player, data, "loot_chest", null);
                            data.addLootedChest(event.getPos());
                        }
                    }
                });
            }
        }
    }

    private void applyRule(Player player, String triggerType, Entity target) {
        player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data -> applyRule((ServerPlayer) player, data, triggerType, target));
    }

    private void applyRule(ServerPlayer player, IJudgeData data, String triggerType, Entity target) {
        JudgeDataManager.getActionRules().stream().filter(r -> r.trigger.equals(triggerType)).forEach(rule -> {
            if (rule.target != null && !rule.target.equals("any") && target != null) {
                boolean match = false;
                if (rule.target.startsWith("#")) {
                    TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(rule.target.substring(1)));
                    if (target.getType().is(tag)) match = true;
                    
                    // Fallback for common tags if tag system fails
                    if (!match && rule.target.equals("#minecraft:animals") && target instanceof Animal) match = true;
                    if (!match && rule.target.equals("#minecraft:monsters") && target instanceof Enemy) match = true;
                } else {
                    if (ForgeRegistries.ENTITY_TYPES.getKey(target.getType()).toString().equals(rule.target)) match = true;
                }
                if (!match) return;
            }
            if (rule.condition != null) {
                if ("player_not_hungry".equals(rule.condition) && player.getFoodData().getFoodLevel() < 20) return;
                if ("is_tamed".equals(rule.condition)) {
                    if (!(target instanceof TamableAnimal) || !((TamableAnimal) target).isTame()) return;
                }
            }
            
            long now = System.currentTimeMillis();
            if (rule.cooldownSeconds > 0) {
                List<Long> timestamps = data.getActionTimestamps(rule.id);
                if (!timestamps.isEmpty() && (now - timestamps.get(timestamps.size() - 1)) < rule.cooldownSeconds * 1000) return;
            }
            if (rule.cap > 0 && rule.capDurationMinutes > 0) {
                long cutoff = now - TimeUnit.MINUTES.toMillis(rule.capDurationMinutes);
                if (data.getActionTimestamps(rule.id).stream().filter(t -> t > cutoff).count() >= rule.cap) return;
            }

            int combo = 1;
            if (rule.combo_window_seconds > 0) {
                if (rule.id.equals(data.getComboActionKey()) && now < data.getComboExpireTime()) {
                    combo = data.getComboCount() + 1;
                }
                data.setComboActionKey(rule.id);
                data.setComboCount(combo);
                data.setComboExpireTime(now + rule.combo_window_seconds * 1000);
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
            PacketHandler.sendToPlayer(new PacketJudgePopup(rule.id, delta, combo), player);
            syncData(player);
        });
    }

    private void applyThresholdEffects(ServerPlayer player, IJudgeData data) {
        int score = data.getLifeScore();
        for (JudgeDataManager.ThresholdTier tier : JudgeDataManager.getThresholdTiers()) {
            boolean applies = tier.isNegative ? score <= tier.threshold : score >= tier.threshold;
            if (applies) {
                MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(tier.effectId));
                if (effect != null) player.addEffect(new MobEffectInstance(effect, 10 * 20, tier.amplifier, true, false));
            }
        }
    }

    private void syncData(ServerPlayer player) {
        player.getCapability(JudgeDataProvider.JUDGE_DATA).ifPresent(data -> PacketHandler.sendToPlayer(new PacketSyncJudgeData(data.getLifeScore(), data.getJudgeLevel(), data.getLastDeathLifeScore(), data.getFracturedComposureStacks()), player));
    }
}
