# PvP Hub: a PvPHQ simulator for singleplayer

A Fabric mod for Minecraft 26.2 that turns a singleplayer world into a make-believe PvPHQ: a busy lobby full of
players, ranked and unranked queues, huge arenas, tiers, kit editing and live fights to watch. Every other "player" is a
VexBot, so install the **VexBot** mod as well. The mod does nothing on a dedicated server.

## Install
Put Fabric API, VexBot and `pvphub-0.1.0.jar` in `.minecraft/mods`, make a new **Superflat** world and open it. The
first time, the lobby is built at once (a few seconds); the six duel colosseums and the FFA colosseum are then built in
the background (watch the sidebar), each 255 blocks across.

## The lobby
A floating island at night: a red-carpet plaza with the spawn plinth, wooden walks, flower beds and stone walls, four
plank paths with lamp posts, timber houses, the giant pumpkin stage under the TOURS letters with the live **Ranked
Fights** board, the KIT and FFA halls, and the PVPHQ letters behind you. NPCs (click them): Money SMP, UHC Games,
Ranked Queue, Unranked Queue, Kit Editor, FFA, Ranked Fights, Leaderboards, Region Selector.

## Hotbar
Iron sword: Unranked Queue · Golden sword: Ranked Queue · Cake: Events (FFA, quick duel) · Creeper head: Profile & Stats ·
Book: Kit Editor · Spyglass: Ranked Fights · Compass: Region Selector.

## Ranks and tiers
PvPHQ ranks in the tab list and chat: OWNER, DEVELOPER, ADMIN, MANAGER, SENIOR MOD, ARBITER, MOD, HELPER, MONEY SMP,
CRACKBEAST, CREATOR, the personal tags (KITTY, HOST, HEAVEN, ...), HQ+ and default. Pick yours with `/rank <rank>`.
Ratings show as tiers, LT5 up to HT1, with TR points.

## Kit editor
Book or `/kiteditor`: pick a kit, move its items anywhere (armour and offhand too), then click **[CONFIRM KIT EDIT]**
or **[CANCEL KIT EDIT]** in chat (`/kiteditor save|cancel|reset`). Your layout is used every time you get that kit.

## Commands
`/hub` `/queue <kit> [ranked]` `/queue leave` `/duel <player> [kit] [bestof]` `/ffa` `/spectate [player]` `/stats`
`/leaderboard <kit>` `/kiteditor` `/rank <rank>` `/regions` `/pvphub status`

## Settings (`config/pvphub/config.json`)
`serverName`, `address`, `discord`, `store`, `version`, `playerRank`, `simulatedOnline`, `regions`, `crowdBots` (120),
`crowdDuels`, `botOpponents`, `botOpponentDelaySeconds`, `duelSlots`, `arenaBuildColumnsPerTick`, `countdownSeconds`.

## Build
`./gradlew build` (Java 25) writes `build/libs/pvphub-0.1.0.jar`. VexBot is read from `../pvpchamps/libs/vexbot.jar`.
`./gradlew runServer` starts a test server with the mod switched on.
