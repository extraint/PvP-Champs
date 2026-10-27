package com.relay.pvphub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import vexbot.api.VexBotApi;
import vexbot.fakeplayer.bot.BotPlayerActionPack;
import vexbot.fakeplayer.bot.connection.ServerPlayerInterface;
import vexbot.kit.KitManager;

/**
 * A lobby that is never empty (needs VexBot): about a hundred bots wander the lobby and now and then two of them go off to
 * an arena for a duel you can spectate. They cannot be hurt in the lobby, they join and leave silently, and their fights
 * are never rated.
 */
public final class Population {
	private Population() {}

	private static final String[] WORDS = {"shadow", "pixel", "frost", "toxic", "swift", "void", "nova", "ghost", "lunar", "blaze", "storm", "rogue", "neon", "mystic", "turbo", "salty", "clutch",
		"hyper", "arctic", "solar", "feral", "omega", "cosmic", "grim", "aura", "dino", "panda", "tiger", "raven", "bandit", "pilot", "ninja", "cobra", "falcon", "reaper", "wizard", "hawk",
		"viper", "sniper", "knight", "wolf", "fox", "snake", "spike", "crisp", "mango", "pepper", "noodle", "waffle", "toast", "cookie", "sushi", "taco", "pickle", "bean", "lemon", "kiwi"};
	private static final String[] CAPS = {"Failed", "Desquatar", "HowToSwap", "Staryay", "Zotao", "Phobied", "Jxrdan", "Snay", "Dinohams", "Deadramc", "Spikly", "Unkn0wn", "CuriousNick", "Spiller",
		"AuraFarming", "Elproblema", "Locknap", "Corruptus", "Flowtives", "Macguy", "Solsy", "Dodged", "Zylo", "Areomas", "Charmiful", "Darzette", "Deamor", "Dentz", "Dobeer", "Dovevich",
		"Ecaa", "Exoified", "Brandon", "Bedrogk", "Beeizzy", "Arsle", "Angelcide", "Gurrkis", "Solren", "Thewen", "Swagsense", "Adriftic", "Bozkurt", "Squizy", "Istak", "Mvandebuurt"};

	/** A name that looks like a real player's: a mix of lowercase words, capitalised handles, underscores and numbers. */
	private static String name() {
		String n = switch (RNG.nextInt(7)) {
			case 0 -> WORDS[RNG.nextInt(WORDS.length)] + WORDS[RNG.nextInt(WORDS.length)] + (RNG.nextInt(2) == 0 ? "" + RNG.nextInt(1000) : "");
			case 1 -> CAPS[RNG.nextInt(CAPS.length)] + "_";
			case 2 -> "_" + WORDS[RNG.nextInt(WORDS.length)] + WORDS[RNG.nextInt(WORDS.length)];
			case 3 -> CAPS[RNG.nextInt(CAPS.length)] + RNG.nextInt(100);
			case 4 -> cap(WORDS[RNG.nextInt(WORDS.length)]) + cap(WORDS[RNG.nextInt(WORDS.length)]);
			case 5 -> CAPS[RNG.nextInt(CAPS.length)] + (RNG.nextInt(2) == 0 ? "YT" : "TTV");
			default -> WORDS[RNG.nextInt(WORDS.length)] + "_" + RNG.nextInt(10000);
		};
		if (RNG.nextInt(8) == 0) n = n.replace('o', '0');
		return n;
	}

	private static String cap(String w) { return Character.toUpperCase(w.charAt(0)) + w.substring(1); }

	/** Kits the bots can fight well (the crystal and cart kits need PvP Champs' move engine). */
	private static final Kit[] KITS = {Kit.SWORD, Kit.SWORD, Kit.UHC, Kit.NETHERITE_OP, Kit.DIAMOND_SMP, Kit.NETHERITE_SMP, Kit.SPEAR_MACE};

	private static final Random RNG = new Random();
	private static boolean enabled;
	private static final List<String> TO_SPAWN = new ArrayList<>();
	private static final Map<String, Bot> BOTS = new HashMap<>();
	private static final List<Recycle> RECYCLE = new ArrayList<>();
	private static final Set<String> ALL = new LinkedHashSet<>();
	private static int ticks, nextDuel = 10;
	private static boolean started;
	private static int nextChat = 200;
	private static final String[] CHAT = {"gg", "gg", "gg", "GG", "lol", "xd", "L", ".", "haha", "hahahah", "mace", "uhc?", "anyone wanna duel", "noob", "nice", "ez", "bruh", "ggs", "lag",
		"what kit", "sword queue is so long", "yoooo", "hi", "wp", "close one", "ok", "i dont have a mic", "AAAAAH", "your horrible", "geeg", "kiwi", "Jajaa", "gnice", "streaming rn", "1v1 me",
		"who wants to 2v2", "ranked is so sweaty", "just hit HT3", "LT5 forever", "crystal queue dead?", "nethpot anyone", "bro is cheating", "report him", "lag spike", "my ping is 200",
		"spear mace is broken", "netherite op > everything", "rate my kit", "how do i edit kits", "book in hotbar", "w", "fr", "no way", "ratio", "im so tilted", "one more", "rematch?"};
	private static final String[] AT = {"%s whos better u or %s", "gg %s", "%s 1v1?", "yoooo %s", "%s u gotta invite people", "%s q uhc lol", "i love %s", "%s rematch"};

	private static final class Bot {
		final String name;
		boolean ready;
		int wait;                                            // ticks until it decides what to do next
		Bot(String name) { this.name = name; }
	}

	private record Recycle(String name, int at) {}

	/** A new world: start the crowd over. */
	public static void reset() {
		started = false;
		Crowd.active = false;
		TO_SPAWN.clear(); BOTS.clear(); RECYCLE.clear();
		ticks = 0; nextDuel = 10; nextChat = 200;
	}

	public static void enable() { enabled = true; }

	public static boolean available() { return enabled && started; }

	private static void begin(MinecraftServer server) {
		started = true;
		Crowd.active = true;
		int want = Config.get().crowdBots;
		while (ALL.size() < want) {
			String n = name();
			if (n.length() < 3 || n.length() > 16 || !ALL.add(n)) continue;
		}
		for (String n : ALL) { Crowd.add(n); TO_SPAWN.add(n); }
		java.util.Collections.shuffle(TO_SPAWN, RNG);
	}

	private static Vec3 spot() {
		Config c = Config.get();
		double a = RNG.nextDouble() * Math.PI * 2, r = 6 + RNG.nextDouble() * 11;
		return new Vec3(c.lobbyX + Math.cos(a) * r, Lobby.ground() + 1, c.lobbyZ + Math.sin(a) * r);
	}

	private static void spawn(MinecraftServer server, String name) {
		VexBotApi.spawnBot(server, name, spot(), RNG.nextFloat() * 360f, Hub.world(server).dimension().identifier().toString());
		BOTS.put(name, new Bot(name));
		Crowd.setElo(name, 1180 + RNG.nextGaussian() * 220);
		Ranks.randomFor(server, name, RNG);
	}

	/** Gives a bot a kit the way VexBot expects (its armour and offhand managers see the change). */
	public static void give(ServerPlayer p, Kit kit) {
		KitManager.apply(p, kit.build(p.level().getServer()));
		p.setGameMode(GameType.SURVIVAL);
		p.removeAllEffects();
		p.setHealth(p.getMaxHealth());
		p.getFoodData().setFoodLevel(20);
		p.clearFire();
	}

	/** The fight starts: each bot goes for the other. */
	public static void fight(Duel d) {
		for (int i = 0; i < 2; i++) if (Crowd.isBot(d.p[i])) VexBotApi.hunt(d.p[i].getGameProfile().name(), d.p[1 - i].getGameProfile().name());
	}

	/** A bot duel is over: drop both bots and bring them back fresh in a few seconds. */
	public static void recycle(ServerPlayer a, ServerPlayer b) {
		RECYCLE.add(new Recycle(a.getGameProfile().name(), ticks + 2));
		if (b != a) RECYCLE.add(new Recycle(b.getGameProfile().name(), ticks + 2));
	}

	/** An idle bot to stand in as an opponent when nobody else is queueing (null if none). */
	public static ServerPlayer idleBot(MinecraftServer s) {
		List<ServerPlayer> idle = new ArrayList<>();
		for (Bot b : BOTS.values()) {
			if (!b.ready) continue;
			ServerPlayer p = VexBotApi.resolveBot(s, b.name);
			if (p != null && Hub.state(p) == Hub.State.LOBBY && p.isAlive()) idle.add(p);
		}
		if (idle.isEmpty()) return null;
		ServerPlayer p = idle.get(RNG.nextInt(idle.size()));
		BotPlayerActionPack pack = ((ServerPlayerInterface) p).getActionPack();
		if (pack != null) pack.stopAll();
		return p;
	}

	public static void tick(MinecraftServer server) {
		if (!enabled || Arenas.ffa() == null) return;
		if (!started) { if (server.getTickCount() < 100) return; begin(server); }
		ticks++;
		MinecraftServer s = server;

		if (ticks == 1 || ticks == 200) {                                     // VexBot keeps bots from the last session: send away any that are not ours
			for (String n : new ArrayList<>(VexBotApi.listBots())) if (!ALL.contains(n)) VexBotApi.despawnBot(s, n);
		}
		if (ticks % 4 == 0 && !TO_SPAWN.isEmpty()) spawn(s, TO_SPAWN.remove(TO_SPAWN.size() - 1));      // fifteen a second at most

		List<Recycle> due = new ArrayList<>();                                  // take the due ones out first: despawning can add new entries
		for (Recycle r : RECYCLE) if (r.at <= ticks) due.add(r);
		RECYCLE.removeAll(due);
		for (Recycle r : due) {
			if (VexBotApi.isBot(s, r.name)) {
				VexBotApi.despawnBot(s, r.name);
				BOTS.remove(r.name);
				RECYCLE.add(new Recycle("+" + r.name, ticks + 60));          // "+" marks "bring it back"
			} else if (r.name.startsWith("+")) {
				spawn(s, r.name.substring(1));
			}
		}

		int i = 0;
		List<String> lost = new ArrayList<>();
		for (Bot b : BOTS.values()) {
			i++;
			ServerPlayer p = VexBotApi.resolveBot(s, b.name);
			if (p == null) {
				if (b.ready && !pendingRecycle(b.name)) { lost.add(b.name); }          // a bot that dropped off: bring it back
				continue;
			}
			if (!b.ready) {                                                  // just arrived: settle it into the lobby
				b.ready = true;
				int ms = RNG.nextInt(10) == 0 ? 1 : 12 + (int) Math.abs(RNG.nextGaussian() * 45);        // a few ping-less, most between 12 and ~100
				if (p.connection != null) ((com.relay.pvphub.mixin.CommonListenerAccess) p.connection).pvphub$setLatency(ms);
				p.setGameMode(GameType.ADVENTURE);
				p.getInventory().clearContent();
				Hub.setState(p, Hub.State.LOBBY);
				continue;
			}
			if (Hub.state(p) != Hub.State.LOBBY) continue;
			if (p.getY() < Config.get().lobbyY - 4) { Vec3 v = spot(); Hub.teleport(p, Hub.world(s), v.x, v.y, v.z, 0f); }
			p.getFoodData().setFoodLevel(20);
			if ((ticks + i) % 10 == 0) wander(p, b);
		}

		for (String n : lost) { BOTS.remove(n); TO_SPAWN.add(n); }
		if (ticks % 20 == 0) duels(s);
		if (--nextChat <= 0) { chat(s); nextChat = 30 + RNG.nextInt(120); }
	}

	/** One bot says something in chat. */
	private static void chat(MinecraftServer s) {
		List<Bot> ready = new ArrayList<>();
		for (Bot b : BOTS.values()) if (b.ready) ready.add(b);
		if (ready.isEmpty()) return;
		ServerPlayer p = VexBotApi.resolveBot(s, ready.get(RNG.nextInt(ready.size())).name);
		if (p == null) return;
		String msg = CHAT[RNG.nextInt(CHAT.length)];
		if (RNG.nextInt(5) == 0 && ready.size() > 2) {                        // now and then someone talks to someone
			String a = ready.get(RNG.nextInt(ready.size())).name, b2 = ready.get(RNG.nextInt(ready.size())).name;
			msg = String.format(AT[RNG.nextInt(AT.length)], a, b2);
		}
		s.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.empty().append(p.getDisplayName()).append(net.minecraft.network.chat.Component.literal("§f: " + msg)), false);
	}

	private static boolean pendingRecycle(String name) {
		for (Recycle r : RECYCLE) if (r.name.equals(name) || r.name.equals("+" + name)) return true;
		return false;
	}

	/** Walk a little, stop, look about; turn back when near the edge. */
	private static void wander(ServerPlayer p, Bot b) {
		b.wait -= 10;
		if (b.wait > 0) return;
		BotPlayerActionPack pack = ((ServerPlayerInterface) p).getActionPack();
		if (pack == null) return;
		Config c = Config.get();
		double dx = c.lobbyX - p.getX(), dz = c.lobbyZ - p.getZ();
		boolean far = Math.hypot(dx, dz) > 16;
		int roll = RNG.nextInt(10);
		if (roll < 3 && !far) {                                              // stand and look around
			pack.stopMovement();
			pack.look(RNG.nextFloat() * 360f - 180f, RNG.nextFloat() * 30f - 10f);
			b.wait = 40 + RNG.nextInt(100);
			return;
		}
		float yaw = far ? (float) Math.toDegrees(Math.atan2(-dx, dz)) + RNG.nextFloat() * 60f - 30f : RNG.nextFloat() * 360f - 180f;
		pack.look(yaw, 0f);
		pack.setForward(1f);
		pack.setSprinting(RNG.nextInt(4) == 0);
		if (RNG.nextInt(5) == 0) pack.start(BotPlayerActionPack.ActionType.JUMP, BotPlayerActionPack.Action.once());
		b.wait = 30 + RNG.nextInt(90);
	}

	/** Every so often two idle bots take an arena and fight; a fight that drags on is ended. */
	private static void duels(MinecraftServer s) {
		int running = 0;
		for (Duel d : Duels.active()) {
			if (!d.simulated) continue;
			running++;
			if (d.phase == Duel.Phase.FIGHT && d.fightTicks > 20 * 150) d.died(d.p[RNG.nextInt(2)]);          // two and a half minutes is enough
		}
		Config c = Config.get();
		if (--nextDuel > 0 || running >= c.crowdDuels || Arenas.free() <= c.crowdReserveArenas) return;
		List<ServerPlayer> idle = new ArrayList<>();
		for (Bot b : BOTS.values()) {
			if (!b.ready) continue;
			ServerPlayer p = VexBotApi.resolveBot(s, b.name);
			if (p != null && Hub.state(p) == Hub.State.LOBBY && p.isAlive()) idle.add(p);
		}
		if (idle.size() < 2) return;
		ServerPlayer a = idle.remove(RNG.nextInt(idle.size())), b = idle.remove(RNG.nextInt(idle.size()));
		BotPlayerActionPack pa = ((ServerPlayerInterface) a).getActionPack(), pb = ((ServerPlayerInterface) b).getActionPack();
		if (pa != null) pa.stopAll();
		if (pb != null) pb.stopAll();
		Kit kit = KITS[RNG.nextInt(KITS.length)];
		Duels.start(a, b, kit, true, RNG.nextInt(4) == 0 ? 3 : 1, true);
		nextDuel = 8 + RNG.nextInt(20);
	}
}
