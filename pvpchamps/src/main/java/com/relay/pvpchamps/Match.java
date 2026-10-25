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
	public Kits kit;
	/** One kit per game, for series that draft them; null means every game uses {@link #kit}. */
	private java.util.List<Kits> gameKits;
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
	/** Per fighter (0 = A, 1 = B): where it was last seen alive, and how long a respawn has been pending. */
	private final Vec3[] lastPos = new Vec3[2];
	private final int[] respawnWait = new int[2];
	private int respawnsLeft = 2;
	private boolean loserDecided, aLost;
	private net.minecraft.world.item.ItemStack[] kitStacks;
	private final java.util.Map<String, String> barSeen = new java.util.HashMap<>();
	private int holeA, holeB; // ticks each fighter has spent well below the arena floor

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
		this.centre = MatchManager.origin() != null ? MatchManager.origin() : human.position(); // tournaments stage every match where they began
		this.savedMode = human.gameMode();
		if (!watch) {
			saved.stacks = new net.minecraft.world.item.ItemStack[41];
			for (int i = 0; i < 41; i++) saved.stacks[i] = human.getInventory().getItem(i).copy();
		}
		vexbot.BotSettings.botDontLeave = true; // settings loaded after startup can flip these back
		vexbot.BotSettings.botLeaveOnDeath = false;
		vexbot.BotSettings.shulkerRegear = false; // no placing shulker boxes (they ended up as a wall)
		vexbot.BotSettings.enderChestRegear = false;
		vexbot.BotSettings.pearlChasing = false; // two bots chasing each other with pearls overshoot and loop; PearlClose throws one aimed pearl
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

	/** Waits are written in ticks at the normal 20 per second; at a faster /pvpchamps speedup they stretch so real time stays the same. */
	private int sc(int ticks) {
		float rate = server.tickRateManager().tickrate();
		return Math.round(ticks * Math.max(1f, rate / 20f));
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

	/** Sets the kit for each game of the series (game 1 should be the kit passed to the constructor). */
	public Match setGameKits(java.util.List<Kits> kits) {
		this.gameKits = kits;
		return this;
	}

	private ArenaGuard arena;

	private void beginRound() {
		if (arena == null) { arena = Arena.guard(level); if (arena == null) arena = new ArenaGuard(level, centre); } else arena.restore();
		if (gameKits != null && round - 1 < gameKits.size() && gameKits.get(round - 1) != kit) {
			kit = gameKits.get(round - 1);
			kitStacks = kit.build(server);
			message(Component.literal("§eGame " + round + " kit: " + kit.label));
		}
		Roster.applyTier(nameB, kit);
		if (watch) Roster.applyTier(nameA, kit);
		state = State.COUNTDOWN;
		timer = sc(60);
		loserDecided = false;
		missingA = missingB = fightTicks = 0;
		gearSeen.clear();
		lastPos[0] = lastPos[1] = null;
		respawnWait[0] = respawnWait[1] = 0;
		respawnsLeft = 2;
		VexBotApi.despawnBot(server, nameB);
		if (watch) VexBotApi.despawnBot(server, nameA);
		revive();
		System.err.println("[pvpchamps] round " + round + " begins: " + nameA + " vs " + nameB + " in " + kit.label + " (bots are respawned for it)");
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
		if (centre.equals(Arena.spot())) return new Vec3(x, centre.y, z); // inside the arena: stand on the mud, not the roof
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
		return new Vec3(x, Math.max(y, centre.y - 2), z);
	}

	private void prepare(ServerPlayer p) {
		KitManager.apply(p, p.getUUID().equals(humanId) && !watch ? Layouts.apply(p, kit, kitStacks) : kitStacks);
		p.setGameMode(GameType.SURVIVAL);
		p.setHealth(p.getMaxHealth());
		p.getFoodData().setFoodLevel(20);
		p.removeAllEffects();
		p.clearFire();
	}

	public void tick() {
		if (state == State.DONE) return;
		if (human() == null) { // the player left the server
			System.err.println("[pvpchamps] match " + nameA + " vs " + nameB + " cancelled: " + humanId + " left the server");
			finish(false, true);
			return;
		}
		switch (state) {
			case COUNTDOWN -> {
				if (timer == sc(60) || timer == sc(40) || timer == sc(20)) message(Component.literal("§e" + Math.round(timer / (float) sc(20))), true);
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
				} else if (timer > sc(300)) {
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

	/** @return true while a dropped bot is being brought back, so the caller skips its loss checks this tick. */
	private boolean handleBotDrop(int idx, String name, String opponent, ServerPlayer bot, float yaw) {
		if (bot != null && bot.isAlive() && bot.getHealth() > 0) {
			lastPos[idx] = bot.position();
			if (respawnWait[idx] > 0) { // it is back: give it the kit and send it at the opponent again
				prepare(bot);
				VexBotApi.hunt(name, opponent);
				respawnWait[idx] = 0;
				message(Component.literal("§e" + name + " reconnected."));
			}
			return false;
		}
		if (respawnWait[idx] > 0) { // waiting for the respawn; give up after five seconds
			if (++respawnWait[idx] > sc(100)) { respawnWait[idx] = 0; return false; }
			return true;
		}
		if (bot == null && lastPos[idx] != null && !loserDecided) {
			respawnsLeft--;
			System.err.println("[pvpchamps] " + name + " dropped off the server mid-round, respawning it (" + respawnsLeft + " left)");
			VexBotApi.spawnBot(server, name, lastPos[idx], yaw, dimension());
			respawnWait[idx] = 1;
			message(Component.literal("§e" + name + " dropped off the server; bringing it back."));
			return true;
		}
		return false;
	}

	/** A fighter left the server mid-round without dying: tell the player and log it instead of ending silently. */
	private void vanished(String name) {
		if (loserDecided) return;
		System.err.println("[pvpchamps] " + name + " left the server mid-round (not a death), round " + round + " after " + fightTicks + " ticks");
		message(Component.literal("§e" + name + " is out of sight; the round continues until someone dies."));
	}

	/** Called when a fighter dies, from the death event or from polling. */
	public void fighterDown(String name) {
		if (state != State.FIGHT || loserDecided) return;
		ServerPlayer who = name.equalsIgnoreCase(nameA) ? (watch ? VexBotApi.resolveBot(server, nameA) : humanRef) : VexBotApi.resolveBot(server, nameB);
		if (who != null && who.isAlive() && who.getHealth() > 0) { // a death event for someone still standing is not a loss
			System.err.println("[pvpchamps] ignored a death event for " + name + ": still alive at " + who.getHealth());
			return;
		}
		if (name.equalsIgnoreCase(nameA)) { loserDecided = true; aLost = true; }
		else if (name.equalsIgnoreCase(nameB)) { loserDecided = true; aLost = false; }
	}

	private final java.util.Map<String, java.util.Map<String, Integer>> gearSeen = new java.util.HashMap<>();

	/** Diagnostics: logs the moment a weapon or tool leaves a fighter's inventory, and how (dropped in the world vs. broken). */
	private void auditGear(String name, ServerPlayer p) {
		if (p == null || !p.isAlive()) return;
		java.util.Map<String, Integer> now = new java.util.HashMap<>();
		for (int i = 0; i < 41; i++) {
			var st = p.getInventory().getItem(i);
			String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
			if (id.matches(".*(sword|_axe|pickaxe|shovel|mace|spear|shield|elytra|totem).*")) now.merge(id, st.getCount(), Integer::sum);
		}
		StringBuilder bar = new StringBuilder();
		for (int i = 0; i < 9; i++) bar.append(i).append('=').append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(p.getInventory().getItem(i).getItem()).getPath()).append(' ');
		bar.append("off=").append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(p.getInventory().getItem(40).getItem()).getPath());
		String before = barSeen.put(name, bar.toString());
		var was = gearSeen.put(name, now);
		if (was == null) return;
		for (var e : was.entrySet()) {
			int have = now.getOrDefault(e.getKey(), 0);
			if (have < e.getValue() && !e.getKey().contains("totem")) {
				System.err.println("[pvpchamps] " + name + " lost " + e.getKey() + " (" + e.getValue() + " -> " + have + ") at tick " + fightTicks
					+ ", round " + round + ", selected slot " + p.getInventory().getSelectedSlot() + "\n    hotbar before: " + before + "\n    hotbar now:    " + bar);
			}
		}
	}

	private final WebStats.Live liveInfo = new WebStats.Live();

	private void publishLive(ServerPlayer a, ServerPlayer b) {
		liveInfo.id = nameA + "|" + nameB;
		liveInfo.a = nameA; liveInfo.b = nameB;
		liveInfo.kit = kit.label; liveInfo.round = roundLabel(); liveInfo.state = state.name().toLowerCase();
		liveInfo.scoreA = scoreA; liveInfo.scoreB = scoreB; liveInfo.bestOf = bestOf;
		liveInfo.hpA = a == null ? 0 : a.getHealth() + a.getAbsorptionAmount();
		liveInfo.hpB = b == null ? 0 : b.getHealth() + b.getAbsorptionAmount();
		liveInfo.gearA = WebStats.gearOf(a);
		liveInfo.gearB = WebStats.gearOf(b);
		if (liveInfo.startedAt == 0) liveInfo.startedAt = System.currentTimeMillis();
		WebStats.live(liveInfo);
	}

	private float stallHp = -1;
	private int stillFor;

	/**
	 * Stall watchdog, judged by damage rather than movement: running circles without landing a hit still counts as
	 * stalled. If neither fighter has taken damage for 45 seconds, re-issue the hunt; if that does not restart the
	 * fight within 15 more seconds, bring the two together and hunt again.
	 */
	private void stallWatch(ServerPlayer a, ServerPlayer b) {
		if (a == null || b == null) return;
		float hp = a.getHealth() + b.getHealth();
		if (stallHp < 0 || hp < stallHp) stillFor = 0; else stillFor++; // health only goes down in a fight, so any drop is a hit
		stallHp = hp;
		if (stillFor == sc(900)) {
			System.err.println("[pvpchamps] " + nameA + " vs " + nameB + " no damage for 45s; re-issuing the hunt");
			VexBotApi.hunt(nameB, nameA);
			if (watch) VexBotApi.hunt(nameA, nameB);
		} else if (stillFor == sc(1200)) {
			System.err.println("[pvpchamps] " + nameA + " vs " + nameB + " still no damage; bringing them together");
			teleport(b, a.position().add(2.5, 0, 0), 90f);
			VexBotApi.hunt(nameB, nameA);
			if (watch) VexBotApi.hunt(nameA, nameB);
			stillFor = 0;
		}
	}

	private void fightTick() {
		fightTicks++;
		ServerPlayer a = watch ? VexBotApi.resolveBot(server, nameA) : humanRef;
		ServerPlayer b = VexBotApi.resolveBot(server, nameB);
		// The first hunt order can arrive before a bot has finished its warm-up and then be ignored, leaving both standing at
		// their spawn points until the stall watchdog fires: give the order again a few seconds in.
		if (fightTicks == sc(60) || fightTicks == sc(140)) {
			VexBotApi.hunt(nameB, nameA);
			if (watch) VexBotApi.hunt(nameA, nameB);
		}
		if (fightTicks % 5 == 0) { if (watch) auditGear(nameA, a); auditGear(nameB, b); }
		if (fightTicks % Math.max(2, sc(4)) == 1) publishLive(a, b); // about every 0.2 s of real time
		if (fightTicks > sc(100)) stallWatch(a, b);
		if (watch && (fightTicks == sc(5) || fightTicks == sc(25) || fightTicks == sc(60))) { // heads-only fix: make clients redraw the full bodies
			for (ServerPlayer p : new ServerPlayer[] {a, b}) {
				if (p == null) continue;
				p.setInvisible(false);
				p.setPose(net.minecraft.world.entity.Pose.STANDING);
				p.setSwimming(false);
				teleport(p, p.position(), p.getYRot());
			}
		}
		if (fightTicks % 4 == 0) { restockPlaced(a); restockPlaced(b); } // a placed shulker vanishes and restocks the kit
		trackHoles(a, b);
		// A bot that drops off the server mid-round while alive is brought back where it stood, with the kit, up to twice.
		if (watch && handleBotDrop(0, nameA, nameB, a, -90f)) return;
		if (handleBotDrop(1, nameB, nameA, b, 90f)) return;
		// A fighter that is simply gone for a moment is not a loss; wait three seconds before deciding, and say why.
		if (a == null || a.getHealth() <= 0 || !a.isAlive()) {
			if (a != null) fighterDown(nameA); // only a real death ends a round, never a fighter that is merely out of sight
			else if (++missingA == sc(60)) vanished(nameA);
		}
		if (b == null || b.getHealth() <= 0 || !b.isAlive()) {
			if (b != null) fighterDown(nameB);
			else if (++missingB == sc(60)) vanished(nameB);
		}
		if (!loserDecided) return;
		if (aLost) scoreB++; else scoreA++;
		WebStats.round(aLost ? nameB : nameA, aLost ? nameA : nameB, kit.label);
		Spec.roundEnd(humanRef, aLost ? nameA : nameB); // the breakdown opens only now, never on a client-side guess
		message(Component.literal("§6" + (aLost ? nameB : nameA) + " wins round " + round + " (" + scoreA + "-" + scoreB + ")"));
		state = State.BETWEEN;
		timer = sc(140);
	}

	// ----------------------------------------------------------------------- shulker restock and holes

	/** A shulker box placed next to a fighter disappears and restocks the kit (not anchors, totems or the shulker itself). */
	private void restockPlaced(ServerPlayer p) {
		if (p == null || !p.isAlive()) return;
		net.minecraft.core.BlockPos at = p.blockPosition();
		for (int dx = -4; dx <= 4; dx++)
			for (int dz = -4; dz <= 4; dz++)
				for (int dy = -2; dy <= 3; dy++) {
					net.minecraft.core.BlockPos pos = at.offset(dx, dy, dz);
					if (level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock) {
						level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 18);
						restock(p);
						return;
					}
				}
	}

	private static boolean noRestock(net.minecraft.world.item.ItemStack w) {
		var item = w.getItem();
		return item == net.minecraft.world.item.Items.RESPAWN_ANCHOR || item == net.minecraft.world.item.Items.TOTEM_OF_UNDYING;
	}

	private static String stockKey(net.minecraft.world.item.ItemStack w) {
		return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(w.getItem()) + "|"
			+ w.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
	}

	/**
	 * Refills the kit by what each stack is, not by where it sits: a fighter who has rearranged the hotbar keeps their
	 * layout, and nothing is overwritten (the old slot-by-slot refill wrote the kit's glowstone over anchors that had
	 * been moved into its slot). Anchors and totems are never touched. Armour and the offhand are replaced in place;
	 * tools, potions and the shulker box are renewed or given back; stackable items are topped up to the kit's count.
	 */
	private void restock(ServerPlayer p) {
		net.minecraft.world.item.ItemStack[] want = p.getUUID().equals(humanId) && !watch ? Layouts.apply(p, kit, kitStacks) : kitStacks;
		var inv = p.getInventory();
		for (int i = 36; i < 41; i++) { // armour and offhand
			var w = want[i];
			if (!w.isEmpty() && !noRestock(w)) inv.setItem(i, w.copy());
		}
		java.util.Map<String, java.util.List<Integer>> groups = new java.util.LinkedHashMap<>();
		for (int i = 0; i < 36; i++) {
			var w = want[i];
			if (!w.isEmpty() && !noRestock(w)) groups.computeIfAbsent(stockKey(w), k -> new java.util.ArrayList<>()).add(i);
		}
		java.util.List<java.util.Map.Entry<net.minecraft.world.item.ItemStack, Integer>> pending = new java.util.ArrayList<>(); // missing stacks and their kit slots
		for (var g : groups.values()) {
			var sample = want[g.get(0)];
			String key = stockKey(sample);
			java.util.List<Integer> mine = new java.util.ArrayList<>();
			for (int i = 0; i < 36; i++) if (!inv.getItem(i).isEmpty() && stockKey(inv.getItem(i)).equals(key)) mine.add(i);
			if (sample.getMaxStackSize() == 1) { // tools, potions, the shulker box: one per kit entry, renewed in place
				for (int k = 0; k < g.size(); k++) {
					var fresh = want[g.get(k)].copy();
					if (k < mine.size()) inv.setItem(mine.get(k), fresh);
					else pending.add(java.util.Map.entry(fresh, g.get(k)));
				}
			} else { // stackable: top up to the kit's total
				int need = 0, have = 0;
				for (int i : g) need += want[i].getCount();
				for (int i : mine) have += inv.getItem(i).getCount();
				int deficit = need - have;
				if (deficit > 0) System.err.println("[pvpchamps] restock " + p.getGameProfile().name() + ": " + key + " need " + need + " have " + have);
				for (int i : mine) {
					var st = inv.getItem(i);
					int add = Math.min(deficit, st.getMaxStackSize() - st.getCount());
					if (add > 0) { st.grow(add); deficit -= add; }
				}
				for (int i = 0; deficit > 0 && i < g.size(); i++) {
					var fresh = sample.copy();
					fresh.setCount(Math.min(deficit, sample.getMaxStackSize()));
					deficit -= fresh.getCount();
					pending.add(java.util.Map.entry(fresh, g.get(i)));
				}
			}
		}
		// Every missing stack goes back to its own kit slot first, so one cannot take another's place; only then the leftovers
		// look for any empty slot (and drop at the fighter's feet if there is none).
		java.util.List<java.util.Map.Entry<net.minecraft.world.item.ItemStack, Integer>> left = new java.util.ArrayList<>();
		for (var e : pending) {
			if (inv.getItem(e.getValue()).isEmpty()) inv.setItem(e.getValue(), e.getKey());
			else left.add(e);
		}
		for (var e : left) place(inv, e.getKey(), e.getValue());
		p.inventoryMenu.broadcastChanges();
		p.sendSystemMessage(Component.literal("§aRestocked."), true);
	}

	/** Puts a stack in its kit slot if that is empty, otherwise the first empty slot; if the inventory is full it is dropped. */
	private static void place(net.minecraft.world.entity.player.Inventory inv, net.minecraft.world.item.ItemStack stack, int preferred) {
		if (inv.getItem(preferred).isEmpty()) { inv.setItem(preferred, stack); return; }
		for (int i = 0; i < 36; i++) if (inv.getItem(i).isEmpty()) { inv.setItem(i, stack); return; }
		inv.player.drop(stack, false);
	}

	/** Stuck in a hole for 15 seconds: reset the arena and put both fighters back on the floor. */
	private void trackHoles(ServerPlayer a, ServerPlayer b) {
		double floor = centre.y - 4.5;
		holeA = a != null && a.getY() < floor ? holeA + 1 : 0;
		holeB = b != null && b.getY() < floor ? holeB + 1 : 0;
		if (holeA < sc(300) && holeB < sc(300)) return;
		if (arena != null) arena.restore();
		if (a != null) teleport(a, spot(-10), -90f);
		if (b != null) teleport(b, spot(10), 90f);
		holeA = holeB = 0;
		message(Component.literal("§eStuck in a hole too long: the arena was reset and both fighters are back on the floor."));
	}

	// ----------------------------------------------------------------------- end

	public void cancel() { finish(false, true); }

	private void finish(boolean aWon, boolean cancelled) {
		if (state == State.DONE) return;
		state = State.DONE;
		WebStats.endLive(nameA + "|" + nameB);
		if (!cancelled) WebStats.match(aWon ? nameA : nameB, aWon ? nameB : nameA, kit.label, roundPrefix, Math.max(scoreA, scoreB), Math.min(scoreA, scoreB));
		VexBotApi.despawnBot(server, nameB);
		if (arena != null) arena.restore();
		if (watch) VexBotApi.despawnBot(server, nameA);
		revive();
		Spec.stop(humanRef);
		humanRef.setGameMode(savedMode);
		if (!watch && saved.stacks != null) {
			humanRef.getInventory().clearContent(); // KitManager.apply only fills slots, so empty the kit out first
			KitManager.apply(humanRef, saved.stacks);
		}
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
