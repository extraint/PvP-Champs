package com.relay.pvpchamps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * A series' kit draft. Best of 3: the higher seed bans, the lower seed bans, pick, pick, ban, ban, and the kit left is the
 * decider. Best of 5: ban, ban, and the five kits left are the games in a random order. When you are in the series
 * the bans and picks of your side are made by you in a chest menu (in 2v2 you and your teammate take turns, the teammate
 * being a bot that decides on its own turns); every other step is made by the bots.
 */
public final class Draft {
	private enum Act { BAN, PICK }

	private record Step(boolean hi, Act act) {}

	/** The kits a series drafts from (Netherite OP is not one of the event's kits). */
	public static final List<Kits> POOL = List.of(Kits.SWORD, Kits.UHC, Kits.SPEAR_MACE, Kits.CRYSTAL, Kits.CART,
		Kits.DIAMOND_SMP, Kits.NETHERITE_SMP);
	private static final int[] KIT_SLOTS = { 10, 11, 12, 13, 14, 15, 16 };
	private static final Map<Kits, Item> ICON = new EnumMap<>(Kits.class);
	static {
		ICON.put(Kits.SWORD, Items.NETHERITE_SWORD);
		ICON.put(Kits.UHC, Items.GOLDEN_APPLE);
		ICON.put(Kits.SPEAR_MACE, Items.MACE);
		ICON.put(Kits.CRYSTAL, Items.END_CRYSTAL);
		ICON.put(Kits.CART, Items.TNT_MINECART);
		ICON.put(Kits.DIAMOND_SMP, Items.DIAMOND_CHESTPLATE);
		ICON.put(Kits.NETHERITE_SMP, Items.NETHERITE_CHESTPLATE);
	}

	/** The draft waiting on you, if any. */
	private static Draft active;
	private static final Random RNG = new Random();

	private final MinecraftServer server;
	private final Map<String, UUID> humans;      // the real players taking part, by lower-case name
	private final String hi, lo;
	private final int bestOf;
	private final Function<String, List<String>> kitsOf;
	private final Consumer<String> say;
	private final Consumer<List<Kits>> done;
	private final List<Step> steps = new ArrayList<>();
	private final List<Kits> left = new ArrayList<>(POOL);
	private final List<Kits> picks = new ArrayList<>();
	private final Map<Kits, String> banned = new EnumMap<>(Kits.class), picked = new EnumMap<>(Kits.class);
	private final List<String> log = new ArrayList<>();
	private int idx;
	private Step waiting;
	private String waitingAs;
	private int reopenIn;
	private final SimpleContainer container = new SimpleContainer(27);
	private boolean finished;

	private Draft(MinecraftServer server, List<ServerPlayer> humanPlayers, String a, String b, int bestOf,
			Function<String, List<String>> kitsOf, ToIntFunction<String> rating, Consumer<String> say, Consumer<List<Kits>> done) {
		this.server = server;
		this.humans = new java.util.HashMap<>();
		for (ServerPlayer p : humanPlayers) humans.put(p.getGameProfile().name().toLowerCase(), p.getUUID());
		this.hi = rating.applyAsInt(a) >= rating.applyAsInt(b) ? a : b;
		this.lo = hi.equals(a) ? b : a;
		this.bestOf = bestOf;
		this.kitsOf = kitsOf;
		this.say = say;
		this.done = done;
		steps.add(new Step(true, Act.BAN));
		steps.add(new Step(false, Act.BAN));
		if (bestOf < 5) {
			steps.add(new Step(true, Act.PICK));
			steps.add(new Step(false, Act.PICK));
			steps.add(new Step(true, Act.BAN));
			steps.add(new Step(false, Act.BAN));
		}
	}

	/**
	 * Runs the draft between {@code a} and {@code b} (entrants, "A + B" for a team) and hands the games' kits to
	 * {@code done}. {@code humanPlayers} are the real players among the entrants: each makes the bans and picks of their
	 * own turns in a menu (members of a team take turns in the order they are listed); everything else is decided by the
	 * bots. With no real players the draft finishes at once.
	 */
	public static void start(MinecraftServer server, List<ServerPlayer> humanPlayers, String a, String b, int bestOf,
			Function<String, List<String>> kitsOf, ToIntFunction<String> rating, Consumer<String> say, Consumer<List<Kits>> done) {
		Draft d = new Draft(server, humanPlayers, a, b, bestOf, kitsOf, rating, say, done);
		if (!humanPlayers.isEmpty()) {
			active = d;
			say.accept("§eKit draft: " + d.hi + " has the first ban.");
		}
		d.run();
	}

	/** /pvpchamps draft [kit]: makes your ban or pick by name, or reopens the menu. Returns an error message or null. */
	public static String choose(String who, String kitId) {
		Draft d = active;
		if (d == null || d.waiting == null || !who.equalsIgnoreCase(d.waitingAs)) return "It is not your turn to draft.";
		if (kitId == null) {
			ServerPlayer p = d.player();
			if (p != null) d.open(p);
			return null;
		}
		Kits k = Kits.byId(kitId);
		int i = k == null ? -1 : POOL.indexOf(k);
		if (i < 0 || !d.left.contains(k)) return "That kit is not available to " + (d.waiting.act() == Act.BAN ? "ban" : "pick") + ".";
		d.onClick(KIT_SLOTS[i]);
		return null;
	}

	/** True while a draft is waiting on you. */
	public static boolean pending() { return active != null; }

	public static void cancel() {
		if (active == null) return;
		Draft d = active;
		active = null;
		d.finished = true;
		ServerPlayer p = d.player();
		if (p != null && p.containerMenu instanceof DraftMenu) p.closeContainer();
	}

	/** Reopens the menu if you closed it before choosing. */
	public static void tickActive() {
		Draft d = active;
		if (d == null || d.waiting == null) return;
		ServerPlayer p = d.player();
		if (p == null || p.containerMenu instanceof DraftMenu) { d.reopenIn = 0; return; }
		if (++d.reopenIn >= 40) { d.reopenIn = 0; d.open(p); }
	}

	/** The player whose turn it is, or any real player when none is waiting. */
	private ServerPlayer player() {
		UUID id = waitingAs != null ? humans.get(waitingAs.toLowerCase()) : humans.values().stream().findFirst().orElse(null);
		return id == null ? null : server.getPlayerList().getPlayer(id);
	}

	// ----------------------------------------------------------------------- flow

	private void run() {
		while (idx < steps.size() && !finished) {
			Step s = steps.get(idx);
			String by = s.hi() ? hi : lo, opp = s.hi() ? lo : hi;
			String actor = by;
			if (!humans.isEmpty()) {
				int own = 0;
				for (int i = 0; i < idx; i++) if (steps.get(i).hi() == s.hi()) own++;
				List<String> team = List.of(by.split(" \\+ "));
				String member = team.get(own % team.size());               // team members take turns in order
				if (humans.containsKey(member.toLowerCase())) {
					waiting = s;
					waitingAs = member;
					ServerPlayer p = player();
					if (p == null) { finished = true; active = null; return; }
					render();
					open(p);
					return;
				}
				actor = team.size() > 1 ? member : by;
			}
			Kits k = s.act() == Act.BAN ? chooseBan(by, opp) : choosePick(by);
			apply(s, actor, k);
		}
		if (finished) return;
		finish();
	}

	private void apply(Step s, String actor, Kits k) {
		left.remove(k);
		if (s.act() == Act.BAN) {
			banned.put(k, actor);
			say.accept("§c" + actor + " bans " + k.label);
			log.add(actor + " banned " + k.label);
		} else {
			picks.add(k);
			picked.put(k, actor);
			say.accept("§a" + actor + " picks " + k.label);
			log.add(actor + " picked " + k.label);
		}
		idx++;
	}

	private void finish() {
		finished = true;
		List<Kits> games;
		if (bestOf >= 5) {
			games = new ArrayList<>(left);
			Collections.shuffle(games, RNG);
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < games.size(); i++) sb.append(i == 0 ? "" : ", ").append(games.get(i).label);
			say.accept("§7Games: " + sb);
		} else {
			Kits decider = left.get(0);
			say.accept("§7Decider: " + decider.label);
			games = List.of(picks.get(0), picks.get(1), decider);
		}
		if (active == this) {
			active = null;
			ServerPlayer p = player();
			if (p != null && p.containerMenu instanceof DraftMenu) p.closeContainer();
		}
		done.accept(games);
	}

	/** Bans the kit that helps the opponent most: one they play and the banner does not. */
	private Kits chooseBan(String by, String opp) {
		Kits best = null;
		double bestScore = -1e9;
		for (Kits k : left) {
			double score = (kitsOf.apply(opp).contains(k.id) ? 2 : 0) - (kitsOf.apply(by).contains(k.id) ? 1 : 0) + RNG.nextDouble() * 0.5;
			if (score > bestScore) { bestScore = score; best = k; }
		}
		return best;
	}

	/** Picks the banner's own best kit still available (earlier in their kit list means more preferred). */
	private Kits choosePick(String by) {
		Kits best = null;
		double bestScore = -1e9;
		List<String> own = kitsOf.apply(by);
		for (Kits k : left) {
			int i = own.indexOf(k.id);
			double score = (i >= 0 ? 3 - i * 0.2 : 0) + RNG.nextDouble() * 0.5;
			if (score > bestScore) { bestScore = score; best = k; }
		}
		return best;
	}

	// ----------------------------------------------------------------------- menu

	private void onClick(int slot) {
		if (waiting == null || finished) return;
		for (int i = 0; i < KIT_SLOTS.length; i++) {
			if (KIT_SLOTS[i] != slot || !left.contains(POOL.get(i))) continue;
			Step s = waiting;
			waiting = null;
			apply(s, waitingAs, POOL.get(i));
			run();
			if (!finished && waiting != null) { /* run() already re-rendered for your next turn */ }
			else if (!finished) render();
			return;
		}
	}

	private void open(ServerPlayer p) {
		p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new DraftMenu(id, inv, container), Component.literal("Kit draft")));
	}

	private static ItemStack named(Item item, String name, String... lore) {
		ItemStack st = new ItemStack(item);
		st.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		if (lore.length > 0) {
			List<Component> lines = new ArrayList<>();
			for (String l : lore) lines.add(Component.literal(l));
			st.set(DataComponents.LORE, new ItemLore(lines));
		}
		return st;
	}

	private void render() {
		for (int i = 0; i < 27; i++) container.setItem(i, named(Items.GLASS_PANE, " "));
		boolean ban = waiting != null && waiting.act() == Act.BAN;
		String turn = waiting == null ? "§7Waiting for the other side..." : ban ? "§cYour turn: BAN a kit" : "§aYour turn: PICK a kit";
		List<String> order = new ArrayList<>();
		for (int i = 0; i < steps.size(); i++) {
			Step s = steps.get(i);
			String who = s.hi() ? hi : lo;
			order.add((i < idx ? "§8" : i == idx ? "§e" : "§7") + (i + 1) + ". " + who + " " + (s.act() == Act.BAN ? "bans" : "picks"));
		}
		order.add("§7Then: " + (bestOf >= 5 ? "the 5 kits left are the games" : "the last kit left is the decider"));
		container.setItem(4, named(Items.PAPER, turn, order.toArray(new String[0])));
		for (int i = 0; i < KIT_SLOTS.length; i++) {
			Kits k = POOL.get(i);
			ItemStack st;
			if (banned.containsKey(k)) st = named(Items.BARRIER, "§cBanned: " + k.label, "§7by " + banned.get(k));
			else if (picked.containsKey(k)) {
				st = named(ICON.get(k), "§aPicked: " + k.label, "§7by " + picked.get(k));
				st.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			} else st = named(ICON.get(k), "§e" + k.label, waiting == null ? "§7Not your turn" : ban ? "§cClick to ban" : "§aClick to pick");
			container.setItem(KIT_SLOTS[i], st);
		}
		if (!log.isEmpty()) container.setItem(22, named(Items.BOOK, "§6Draft so far", log.stream().map(l -> "§7" + l).toArray(String[]::new)));
		ServerPlayer p = player();
		if (p != null && p.containerMenu instanceof DraftMenu m) m.broadcastFullState();
	}

	private final class DraftMenu extends ChestMenu {
		DraftMenu(int id, Inventory inv, Container c) { super(MenuType.GENERIC_9x3, id, inv, c, 3); }

		@Override
		public void clicked(int slot, int button, ContainerInput input, Player player) {
			if (slot >= 0 && slot < 27) onClick(slot);
			broadcastFullState();   // nothing in here is ever moved; resync the client's view
		}

		@Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
	}
}
