package com.relay.pvpchamps;

import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import vexbot.api.VexBotApi;
import vexbot.kit.KitManager;

/**
 * One best-of-N match between a human and a roster bot ({@code play}) or two roster bots watched by a spectator
 * ({@code watch}). A round is: countdown, spawn the bots, fight until someone dies, a pause to look at the result.
 */
public final class Match {
	private enum State { COUNTDOWN, SPAWNING, FIGHT, BETWEEN, DONE }

	public final MinecraftServer server;
	private final java.util.UUID humanId;     // the player, or the spectator in watch mode
	private ServerPlayer humanRef;            // refreshed every tick: dying and respawning replaces the ServerPlayer object
	public final boolean watch;
	public final String nameA, nameB;         // A is the human in play mode
	public final Kits kit;
	public final int bestOf;
	private final String roundPrefix, event;
	private final Consumer<Boolean> onEnd;    // true if A won
	private final ServerLevel level;
	private final Vec3 centre;
	private final ItemStack41 saved = new ItemStack41();
	private final GameType savedMode;
	private final boolean changedRespawn;

	private State state = State.COUNTDOWN;
	private int timer, round = 1, scoreA, scoreB, missingA, missingB, fightTicks;
	private boolean loserDecided, aLost;
	private net.minecraft.world.item.ItemStack[] kitStacks;

	/** Tiny holder so the saved inventory can be null until captured. */
	private static final class ItemStack41 { net.minecraft.world.item.ItemStack[] stacks; }

	public Match(MinecraftServer server, ServerPlayer human, boolean watch, String a, String b, Kits kit, int bestOf,
			String roundPrefix, String event, Consumer<Boolean> onEnd) {
		this.server = server;
		this.humanId = human.getUUID();
		this.humanRef = human;
		this.watch = watch;
		this.nameA = a;
		this.nameB = b;
		this.kit = kit;
		this.bestOf = bestOf;
		this.roundPrefix = roundPrefix;
		this.event = event;
		this.onEnd = onEnd;
		this.level = (ServerLevel) human.level();
		this.centre = human.position();
		this.savedMode = human.gameMode();
		if (!watch) {
			saved.stacks = new net.minecraft.world.item.ItemStack[41];
			for (int i = 0; i < 41; i++) saved.stacks[i] = human.getInventory().getItem(i).copy();
		}
		this.changedRespawn = true;
		command("gamerule immediate_respawn true"); // so the human never sits on a death screen between rounds
		this.kitStacks = kit.build(server);
		for (String n : new String[] { a, b }) {
			Roster.Opponent o = Roster.byName(n);
			if (o != null) Roster.applyProfile(o);
		}
		message(Component.literal("=== " + roundPrefix + ": " + a + " vs " + b + " - " + kit.label + " - first to "
			+ (bestOf / 2 + 1) + " ==="));
		beginRound();
	}

	public boolean isDone() { return state == State.DONE; }

	/** A dead human would sit on the death screen: respawn them so the next round can place them. */
	private void revive() {
		ServerPlayer p = human();
		if (p != null && !p.isAlive()) humanRef = server.getPlayerList().respawn(p, false, net.minecraft.world.entity.Entity.RemovalReason.KILLED);
	}

	/** The live player object, or null if they left the server. */
	private ServerPlayer human() {
		ServerPlayer p = server.getPlayerList().getPlayer(humanId);
		if (p != null) humanRef = p;
		return p;
	}

	/** Who the named fighter is fighting right now, or null if they are not in this match or it isn't a fight yet. */
	public ServerPlayer opponentOf(String name) {
		if (state != State.FIGHT) return null;
		if (name.equalsIgnoreCase(nameB)) return watch ? VexBotApi.resolveBot(server, nameA) : humanRef;
		if (watch && name.equalsIgnoreCase(nameA)) return VexBotApi.resolveBot(server, nameB);
		return null;
	}

	private String roundLabel() { return roundPrefix + " · Round " + round + " · " + scoreA + "-" + scoreB; }

	// ----------------------------------------------------------------------- round flow

	private void beginRound() {
		state = State.COUNTDOWN;
		timer = 60;
		loserDecided = false;
		missingA = missingB = fightTicks = 0;
		VexBotApi.despawnBot(server, nameB);
		if (watch) VexBotApi.despawnBot(server, nameA);
		revive();
		if (watch) {
			humanRef.setGameMode(GameType.SPECTATOR);
			teleport(humanRef, centre.add(0, 7, 0), 0f);
		} else {
			prepare(humanRef);
			teleport(humanRef, spot(-10), -90f);
		}
		Spec.stop(humanRef);
	}

	private Vec3 spot(double dx) {
		double x = centre.x + dx, z = centre.z;
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
		return new Vec3(x, Math.max(y, centre.y - 2), z);
	}

	private void prepare(ServerPlayer p) {
		KitManager.apply(p, kitStacks);
		p.setGameMode(GameType.SURVIVAL);
		p.setHealth(p.getMaxHealth());
		p.getFoodData().setFoodLevel(20);
		p.removeAllEffects();
		p.clearFire();
	}

	public void tick() {
		if (state == State.DONE) return;
		if (human() == null) { finish(false, true); return; } // the player left the server
		switch (state) {
			case COUNTDOWN -> {
				if (timer == 60 || timer == 40 || timer == 20) message(Component.literal("§e" + (timer / 20)), true);
				if (--timer <= 0) {
					if (watch) {
						VexBotApi.spawnBot(server, nameA, spot(-10), -90f, dimension());
					}
					VexBotApi.spawnBot(server, nameB, spot(10), 90f, dimension());
					state = State.SPAWNING;
					timer = 0;
				}
			}
			case SPAWNING -> {
				timer++;
				ServerPlayer b = VexBotApi.resolveBot(server, nameB);
				ServerPlayer a = watch ? VexBotApi.resolveBot(server, nameA) : humanRef;
				if (a != null && b != null) {
					if (watch) prepare(a);
					prepare(b);
					VexBotApi.hunt(nameB, nameA);
					if (watch) VexBotApi.hunt(nameA, nameB);
					message(Component.literal("§cFIGHT!"), true);
					if (watch) Spec.watch(humanRef, nameA, nameB, kit.label, roundLabel(), event, "");
					else Spec.play(humanRef, nameA, nameB, kit.label, roundLabel(), event);
					state = State.FIGHT;
				} else if (timer > 300) {
					message(Component.literal("§cThe bot did not spawn (is the area loaded and clear?). Match cancelled."));
					finish(false, true);
				}
			}
			case FIGHT -> fightTick();
			case BETWEEN -> {
				if (--timer <= 0) {
					if (scoreA > bestOf / 2 || scoreB > bestOf / 2) finish(scoreA > scoreB, false);
					else { round++; beginRound(); }
				}
			}
			case DONE -> {}
		}
	}

	/** Called when a fighter dies, from the death event or from polling. */
	public void fighterDown(String name) {
		if (state != State.FIGHT || loserDecided) return;
		if (name.equalsIgnoreCase(nameA)) { loserDecided = true; aLost = true; }
		else if (name.equalsIgnoreCase(nameB)) { loserDecided = true; aLost = false; }
	}

	private void fightTick() {
		fightTicks++;
		ServerPlayer a = watch ? VexBotApi.resolveBot(server, nameA) : humanRef;
		ServerPlayer b = VexBotApi.resolveBot(server, nameB);
		if (a == null || a.getHealth() <= 0 || !a.isAlive()) { if (a == null ? ++missingA > 20 : true) fighterDown(nameA); }
		if (b == null || b.getHealth() <= 0 || !b.isAlive()) { if (b == null ? ++missingB > 20 : true) fighterDown(nameB); }
		if (!loserDecided && fightTicks > 20 * 180) { // three minutes: whoever has more health takes it
			if (a != null && b != null) { loserDecided = true; aLost = a.getHealth() < b.getHealth(); }
		}
		if (!loserDecided) return;
		if (aLost) scoreB++; else scoreA++;
		message(Component.literal("§6" + (aLost ? nameB : nameA) + " wins round " + round + " (" + scoreA + "-" + scoreB + ")"));
		state = State.BETWEEN;
		timer = 140;
	}

	// ----------------------------------------------------------------------- end

	public void cancel() { finish(false, true); }

	private void finish(boolean aWon, boolean cancelled) {
		if (state == State.DONE) return;
		state = State.DONE;
		VexBotApi.despawnBot(server, nameB);
		if (watch) VexBotApi.despawnBot(server, nameA);
		revive();
		Spec.stop(humanRef);
		humanRef.setGameMode(savedMode);
		if (!watch && saved.stacks != null) KitManager.apply(humanRef, saved.stacks);
		if (changedRespawn) command("gamerule immediate_respawn false");
		if (!cancelled) message(Component.literal("§a=== " + (aWon ? nameA : nameB) + " wins the match " + Math.max(scoreA, scoreB) + "-" + Math.min(scoreA, scoreB) + " ==="));
		else message(Component.literal("§7Match ended."));
		if (!cancelled && onEnd != null) onEnd.accept(aWon);
	}

	// ----------------------------------------------------------------------- helpers

	private void teleport(ServerPlayer p, Vec3 pos, float yaw) {
		p.teleportTo(level, pos.x, pos.y, pos.z, Set.<Relative>of(), yaw, 0f, true);
	}

	/** VexBot's spawn call takes the dimension id, e.g. minecraft:overworld. */
	private String dimension() { return level.dimension().identifier().toString(); }

	private void command(String cmd) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
	}

	private void message(Component c) { humanRef.sendSystemMessage(c); }

	private void message(Component c, boolean overlay) { humanRef.sendSystemMessage(c, overlay); }
}
