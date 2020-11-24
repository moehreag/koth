package io.github.restioson.koth.game;

import io.github.restioson.koth.game.map.KothMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import xyz.nucleoid.plasmid.game.GameWorld;
import xyz.nucleoid.plasmid.game.event.*;
import xyz.nucleoid.plasmid.game.player.JoinResult;
import xyz.nucleoid.plasmid.game.player.PlayerSet;
import xyz.nucleoid.plasmid.game.rule.GameRule;
import xyz.nucleoid.plasmid.game.rule.RuleResult;
import xyz.nucleoid.plasmid.util.BlockBounds;
import xyz.nucleoid.plasmid.util.ItemStackBuilder;

import java.util.*;
import java.util.stream.Collectors;

public class KothActive {
    private final KothConfig config;

    public final GameWorld gameWorld;
    private final KothMap gameMap;

    private final Object2ObjectMap<ServerPlayerEntity, KothPlayer> participants;
    private final KothSpawnLogic spawnLogic;
    private final KothStageManager stageManager;
    private final Optional<KothTimerBar> timerBar;
    private final KothScoreboard scoreboard;
    private OvertimeState overtimeState = OvertimeState.NOT_IN_OVERTIME;
    private boolean gameFinished;
    private static final int LEAP_INTERVAL_TICKS = 5 * 20; // 5 second cooldown
    private static final double LEAP_VELOCITY = 1.0;
    private boolean pvpEnabled = false;

    private KothActive(GameWorld gameWorld, KothMap map, KothConfig config, Set<ServerPlayerEntity> participants) {
        this.gameWorld = gameWorld;
        this.config = config;
        this.gameMap = map;
        this.spawnLogic = new KothSpawnLogic(gameWorld, map);
        this.participants = new Object2ObjectOpenHashMap<>();

        for (ServerPlayerEntity player : participants) {
            this.participants.put(player, new KothPlayer(player));
        }

        String name;
        if (config.deathmatch) {
            name = "Deathmatch!";
        } else if (config.winnerTakesAll) {
            name = "Winner Takes All";
        } else {
            name = "Longest-reigning Ruler";
        }

        this.scoreboard = new KothScoreboard(gameWorld, name, this.config.winnerTakesAll, this.config.deathmatch);

        this.stageManager = new KothStageManager(config);

        if (this.config.deathmatch) {
            this.timerBar = Optional.empty();
        } else {
            this.timerBar = Optional.of(new KothTimerBar());
        }
    }

    public static void open(GameWorld gameWorld, KothMap map, KothConfig config) {
        Set<ServerPlayerEntity> participants = new HashSet<>(gameWorld.getPlayers());
        KothActive active = new KothActive(gameWorld, map, config, participants);

        gameWorld.openGame(builder -> {
            builder.setRule(GameRule.CRAFTING, RuleResult.DENY);
            builder.setRule(GameRule.PORTALS, RuleResult.DENY);
            builder.setRule(GameRule.PVP, RuleResult.ALLOW);
            builder.setRule(GameRule.HUNGER, RuleResult.DENY);
            builder.setRule(GameRule.FALL_DAMAGE, RuleResult.DENY);
            builder.setRule(GameRule.INTERACTION, RuleResult.ALLOW);
            builder.setRule(GameRule.BLOCK_DROPS, RuleResult.DENY);
            builder.setRule(GameRule.THROW_ITEMS, RuleResult.DENY);
            builder.setRule(GameRule.UNSTABLE_TNT, RuleResult.DENY);

            builder.on(GameOpenListener.EVENT, active::onOpen);
            builder.on(GameCloseListener.EVENT, active::onClose);

            builder.on(OfferPlayerListener.EVENT, player -> JoinResult.ok());
            builder.on(PlayerAddListener.EVENT, active::addPlayer);
            builder.on(PlayerRemoveListener.EVENT, active::removePlayer);

            builder.on(GameTickListener.EVENT, active::tick);
            builder.on(UseItemListener.EVENT, active::onUseItem);

            builder.on(PlayerDeathListener.EVENT, active::onPlayerDeath);
            builder.on(PlayerDamageListener.EVENT, active::onPlayerDamage);
        });
    }

    private boolean onPlayerDamage(ServerPlayerEntity player, DamageSource source, float value) {
        if (!player.isSpectator() && source.isFire()) {
            this.spawnDeadParticipant(player, this.gameWorld.getWorld().getTime());
        }

        return !this.pvpEnabled || (this.config.spawnInvuln && this.gameMap.noPvp.contains(player.getBlockPos()));
    }

    private void maybeGiveBow(ServerPlayerEntity player) {
        if (this.config.hasBow) {
            ItemStack bow = ItemStackBuilder.of(Items.BOW)
                    .addEnchantment(Enchantments.PUNCH, 2)
                    .addEnchantment(Enchantments.INFINITY, 1)
                    .setUnbreakable()
                    .addLore(new LiteralText("Uzoba dutyulwa"))
                    .build();

            player.inventory.insertStack(bow);
        }
    }

    private void onOpen() {
        ServerWorld world = this.gameWorld.getWorld();
        for (ServerPlayerEntity player : this.participants.keySet()) {
            this.spawnParticipant(player);

            if (this.config.hasStick) {
                ItemStack stick = ItemStackBuilder.of(Items.STICK)
                        .addEnchantment(Enchantments.KNOCKBACK, 2)
                        .addLore(new LiteralText("Ndiza kumbetha"))
                        .build();
                player.inventory.insertStack(stick);
            }

            if (this.config.hasBow) {
                this.maybeGiveBow(player);
                ItemStack arrow = ItemStackBuilder.of(Items.ARROW)
                        .addLore(new LiteralText("It seems to always come back to me..."))
                        .build();

                player.inventory.insertStack(arrow);
            }

            if (this.config.hasFeather) {
                ItemStack feather = ItemStackBuilder.of(Items.FEATHER)
                        .addLore(new LiteralText("Bukelani, ndiyinkosi yesibhakabhaka!"))
                        .build();

                if (this.config.hasBow) {
                    player.inventory.insertStack(feather);
                } else {
                    player.equipStack(EquipmentSlot.OFFHAND, feather);
                }
            }
        }
        this.stageManager.onOpen(world.getTime(), this.config, this.gameWorld);
        this.scoreboard.renderTitle();
    }

    private void onClose() {
        this.timerBar.ifPresent(KothTimerBar::close);
        if (this.scoreboard != null) {
             this.scoreboard.close();
        }
    }

    private void addPlayer(ServerPlayerEntity player) {
        if (!this.participants.containsKey(player)) {
            this.spawnSpectator(player);
        }

        this.timerBar.ifPresent(bar -> bar.addPlayer(player));
    }

    private void removePlayer(ServerPlayerEntity player) {
        this.participants.remove(player);
        this.timerBar.ifPresent(bar -> bar.removePlayer(player));
    }

    private ActionResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        this.spawnDeadParticipant(player, this.gameWorld.getWorld().getTime());
        return ActionResult.FAIL;
    }

    private TypedActionResult<ItemStack> onUseItem(ServerPlayerEntity player, Hand hand) {
        ItemStack heldStack = player.getStackInHand(hand);

        if (heldStack.getItem() == Items.FEATHER) {
            ItemCooldownManager cooldown = player.getItemCooldownManager();
            if (!cooldown.isCoolingDown(heldStack.getItem())) {
                KothPlayer state = this.participants.get(player);
                if (state != null) {
                    Vec3d rotationVec = player.getRotationVec(1.0F);
                    player.setVelocity(rotationVec.multiply(LEAP_VELOCITY));
                    Vec3d oldVel = player.getVelocity();
                    player.setVelocity(oldVel.x, oldVel.y + 0.1f, oldVel.z);
                    player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));

                    player.playSound(SoundEvents.ENTITY_HORSE_SADDLE, SoundCategory.PLAYERS, 1.0F, 1.0F);
                    cooldown.set(heldStack.getItem(), LEAP_INTERVAL_TICKS);
                }
            }
        }

        return TypedActionResult.pass(ItemStack.EMPTY);
    }

    private void spawnDeadParticipant(ServerPlayerEntity player, long time) {
        this.spawnLogic.resetPlayer(player, GameMode.SPECTATOR);
        this.spawnLogic.spawnPlayer(player);

        Inventories.remove(player.inventory, it -> it.getItem() == Items.BOW, 1, false);

        if (this.config.deathmatch) {
            PlayerSet players = this.gameWorld.getPlayerSet();
            players.sendMessage(player.getDisplayName().shallowCopy().append(" has been eliminated!").formatted(Formatting.GOLD));
            players.sendSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP);
        } else {
            this.participants.get(player).deadTime = time;
        }
    }

    private void spawnParticipant(ServerPlayerEntity player) {
        if (this.config.hasBow && !player.inventory.containsAny(new HashSet<>(Collections.singletonList(Items.BOW)))) {
            this.maybeGiveBow(player);
        }

        this.spawnLogic.resetPlayer(player, GameMode.ADVENTURE);
        this.spawnLogic.spawnPlayer(player);
    }

    private void spawnSpectator(ServerPlayerEntity player) {
        this.spawnLogic.resetPlayer(player, GameMode.SPECTATOR);
        this.spawnLogic.spawnPlayer(player);
    }

    private void tick() {
        ServerWorld world = this.gameWorld.getWorld();
        long time = world.getTime();

        for (ArrowEntity arrow : world.getEntitiesByType(EntityType.ARROW, this.gameMap.bounds.toBox(), e -> e.inGround)) {
            arrow.remove();
        }

        boolean overtime = false;
        int alivePlayers = 0;
        int playersOnThrone = 0;

        for (ServerPlayerEntity player : this.participants.keySet()) {
            if (!player.isSpectator()) {
                alivePlayers += 1;
            } else {
                continue;
            }

            boolean onThrone = this.gameMap.throne.toBox().intersects(player.getBoundingBox());

            if (onThrone) {
                playersOnThrone += 1;
            }
        }

        overtime = playersOnThrone != 1;
        overtime |= this.config.deathmatch && alivePlayers > 1;

        KothStageManager.TickResult result = this.stageManager.tick(time, gameWorld, overtime, this.gameFinished);

        switch (result) {
            case CONTINUE_TICK:
                this.pvpEnabled = true;
                this.timerBar.ifPresent(bar -> bar.update(this.stageManager.finishTime - time, this.config.timeLimitSecs * 20));
                break;
            case OVERTIME:
                if (this.overtimeState == OvertimeState.NOT_IN_OVERTIME) {
                    this.overtimeState = OvertimeState.IN_OVERTIME;
                    KothActive.broadcastTitle(new LiteralText("Overtime!"), this.gameWorld);
                    this.timerBar.ifPresent(KothTimerBar::setOvertime);
                } else if (this.overtimeState == OvertimeState.JUST_ENTERED_OVERTIME) {
                    this.overtimeState = OvertimeState.IN_OVERTIME;
                }

                break;
            case NEXT_ROUND:
                for (ServerPlayerEntity participant : this.participants.keySet()) {
                    this.spawnParticipant(participant);
                }
            case TICK_FINISHED_PLAYERS_FROZEN:
                this.pvpEnabled = false;
            case TICK_FINISHED:
                return;
            case GAME_FINISHED:
                this.broadcastWin(this.getWinner());
                return;
            case GAME_CLOSED:
                this.gameWorld.close();
                return;
        }

        for (ServerPlayerEntity player : this.participants.keySet()) {
            player.setHealth(20.0f);

            BlockBounds bounds = this.gameMap.bounds;
            BlockPos pos = player.getBlockPos();
            if (!bounds.contains(pos)) {
                BlockPos max = this.gameMap.bounds.getMax();
                BlockPos playerBoundedY = new BlockPos(pos.getX(), max.getY(), pos.getZ());

                // Allow the player to jump above the bounds but not go out of its x and z bounds
                boolean justAbove = player.getY() > max.getY() && bounds.contains(playerBoundedY);

                if (player.isSpectator()) {
                    this.spawnLogic.spawnPlayer(player);
                } else if (!justAbove) {
                    this.spawnDeadParticipant(player, time);
                }
            }

            if (this.config.deathmatch) {
                continue;
            }

            KothPlayer state = this.participants.get(player);
            assert state != null;

            if (player.isSpectator()) {
                this.tickDead(player, state, time);
                continue;
            }

            if (this.config.winnerTakesAll) {
                List<KothPlayer> top = this.participants.values().stream()
                        .sorted(Comparator.comparingDouble(p -> -p.player.getY())) // Descending sort
                        .limit(1)
                        .collect(Collectors.toList());
                this.scoreboard.render(top);
                continue;
            }

            if (this.gameMap.throne.toBox().intersects(player.getBoundingBox()) && time % 20 == 0) {
                state.score += 1;
                player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.0f);
                player.addExperienceLevels(1);
                this.scoreboard.render(this.buildLeaderboard());
            }
        }
    }

    private void tickDead(ServerPlayerEntity player, KothPlayer state, long time) {
        int sec = 5 - (int) Math.floor((time - state.deadTime) / 20.0f);

        if (sec > 0 && (time - state.deadTime) % 20 == 0) {
            Text text = new LiteralText(String.format("Respawning in %ds", sec)).formatted(Formatting.BOLD);
            player.sendMessage(text, true);
        }

        if (time - state.deadTime > 5 * 20) {
            this.spawnParticipant(player);
        }
    }

    private List<KothPlayer> buildLeaderboard() {
        assert !this.config.winnerTakesAll;
        return this.participants.values().stream()
                .filter(player -> player.score != 0)
                .sorted(Comparator.comparingInt(player -> -player.score)) // Descending sort
                .limit(5)
                .collect(Collectors.toList());
    }


    protected static void broadcastTitle(Text message, GameWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            TitleS2CPacket packet = new TitleS2CPacket(TitleS2CPacket.Action.TITLE, message, 1, 5,  3);
            player.networkHandler.sendPacket(packet);
        }
    }

    private void broadcastWin(ServerPlayerEntity winner) {
        KothPlayer participant = this.participants.get(winner);
        participant.wins++;
        String wonThe;

        if (this.config.deathmatch) {
            List<KothPlayer> top = this.participants.values().stream()
                    .sorted(Comparator.comparingDouble(p -> -p.wins)) // Descending sort
                    .limit(5)
                    .collect(Collectors.toList());
            this.scoreboard.render(top);
        }

        if (participant.wins == this.config.firstTo) {
            wonThe = "game";
            this.gameFinished = true;
        } else {
            wonThe = "round";
        }

        Text message = winner.getDisplayName().shallowCopy().append(" has won the ").append(wonThe).append("!").formatted(Formatting.GOLD);

        PlayerSet players = this.gameWorld.getPlayerSet();
        players.sendMessage(message);
        players.sendSound(SoundEvents.ENTITY_VILLAGER_YES);
    }

    private ServerPlayerEntity getWinner() {
        if (this.config.deathmatch) {
            for (ServerPlayerEntity player : this.participants.keySet()) {
                if (!player.isSpectator()) {
                    return player;
                }
            }
        }

        Map.Entry<ServerPlayerEntity, KothPlayer> winner = null;
        for (Map.Entry<ServerPlayerEntity, KothPlayer> entry : this.participants.entrySet()) {
            if (this.config.winnerTakesAll) {
                if (entry.getKey().isSpectator()) {
                    continue;
                }

                if (winner == null || winner.getKey().getBlockPos().getY() < entry.getKey().getBlockPos().getY() ) {
                    winner = entry;
                }
            } else {
                if (winner == null || winner.getValue().score < entry.getValue().score) {
                    winner = entry;
                }
            }
        }

        return winner != null ? winner.getKey() : null;
    }

    enum OvertimeState {
        NOT_IN_OVERTIME,
        JUST_ENTERED_OVERTIME,
        IN_OVERTIME,
    }
}
