package io.github.restioson.koth.game;

import io.github.restioson.koth.game.map.KothMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;
import xyz.nucleoid.plasmid.game.GameWorld;
import xyz.nucleoid.plasmid.game.event.*;
import xyz.nucleoid.plasmid.game.player.JoinResult;
import xyz.nucleoid.plasmid.game.player.PlayerSet;
import xyz.nucleoid.plasmid.game.rule.GameRule;
import xyz.nucleoid.plasmid.game.rule.RuleResult;
import xyz.nucleoid.plasmid.util.ItemStackBuilder;

import java.util.*;
import java.util.stream.Collectors;

public class KothActive {
    private final KothConfig config;

    public final GameWorld gameWorld;
    private final KothMap gameMap;

    private final Object2ObjectMap<ServerPlayerEntity, KothPlayer> participants;
    private final KothSpawnLogic spawnLogic;
    private final KothIdle idle;
    private final KothTimerBar timerBar;
    private final KothScoreboard scoreboard;

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

        this.scoreboard = new KothScoreboard(gameWorld, name, this.config.winnerTakesAll);

        this.idle = new KothIdle(config);
        this.timerBar = new KothTimerBar();
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

            builder.on(PlayerDeathListener.EVENT, active::onPlayerDeath);
            builder.on(PlayerDamageListener.EVENT, active::onPlayerDamage);
        });
    }

    private boolean onPlayerDamage(ServerPlayerEntity player, DamageSource source, float value) {
        return this.gameMap.noPvp.contains(player.getBlockPos());
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
        }
        this.idle.onOpen(world.getTime(), this.config, this.gameWorld);
        this.scoreboard.renderTitle();
    }

    private void onClose() {
        this.timerBar.close();
        if (this.scoreboard != null) {
             this.scoreboard.close();
        }
    }

    private void addPlayer(ServerPlayerEntity player) {
        if (!this.participants.containsKey(player)) {
            this.spawnSpectator(player);
        }
        this.timerBar.addPlayer(player);
    }

    private void removePlayer(ServerPlayerEntity player) {
        this.participants.remove(player);
        this.timerBar.removePlayer(player);
    }

    private ActionResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        this.spawnParticipant(player);
        return ActionResult.FAIL;
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

        KothIdle.IdleTickResult result = this.idle.tick(time, gameWorld);

        switch (result) {
            case CONTINUE_TICK:
                break;
            case TICK_FINISHED:
                return;
            case GAME_FINISHED:
                this.broadcastWin(this.getWinner());
                return;
            case GAME_CLOSED:
                this.gameWorld.close();
                return;
        }

        this.timerBar.update(this.idle.finishTime - time, this.config.timeLimitSecs * 20);

        for (ServerPlayerEntity player : this.participants.keySet()) {
            player.setHealth(20.0f);

            if (!this.gameMap.bounds.contains(player.getBlockPos())) {
                if (player.isSpectator()) {
                    this.spawnLogic.spawnPlayer(player);
                } else {
                    this.spawnDeadParticipant(player, time);
                }
            }

            if (!this.config.deathmatch) {
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
    }

    private void tickDead(ServerPlayerEntity player, KothPlayer state, long time) {
        int sec = 5 - (int) Math.floor((time - state.deadTime) / 20.0f);

        if (sec > 0 && (time - state.deadTime) % 20 == 0) {
            Text text = new LiteralText(String.format("Respawning in %ds", sec)).formatted(Formatting.BOLD);
            player.sendMessage(text, true);
        }

        if (time - state.deadTime > 5 * 20) {
            this.spawnParticipant(player);
            this.maybeGiveBow(player);
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
        Text message;
        if (winner != null) {
             message = winner.getDisplayName().shallowCopy().append(" has won the game!").formatted(Formatting.GOLD);
        } else {
            message = new LiteralText("The game ended, but nobody won!").formatted(Formatting.GOLD);
        }

        PlayerSet players = this.gameWorld.getPlayerSet();
        players.sendMessage(message);
        players.sendSound(SoundEvents.ENTITY_VILLAGER_YES);
    }

    private ServerPlayerEntity getWinner() {
        if (this.config.deathmatch) {
            ServerPlayerEntity winner = null;
            for (ServerPlayerEntity player : this.participants.keySet()) {
                if (!player.isSpectator()) {
                    if (winner != null) {
                        return null;
                    }
                    winner = player;
                }
            }

            return winner;
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
}
