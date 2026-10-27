# PvP Hub

A server-side Fabric mod (Minecraft 26.2) that turns a server into a PvPHQ-style PvP network. Players join with a normal client; no client mod.

## What it does
- **Lobby** with a hotbar menu: Unranked queue, Ranked queue, FFA, Stats & leaderboards, Spectate, Back to lobby. Nothing in the lobby can be broken, used or damaged.
- **Duel queues** for 8 kits (Sword, UHC, Spear & Mace, Crystal, Cart, Netherite OP, Diamond SMP, Netherite SMP). Ranked duels use Elo (K=32) with ranks Bronze to Champion. Matching looks for a close rating **and** a close ping; both windows widen the longer you wait.
- **Instanced arenas** (6 by default), reset between rounds, 5 second countdown, best-of support, forfeit on disconnect.
- **FFA** arena: instant respawn with the kit, 3 s spawn protection, kill-streak announcements.
- **/duel** requests with a kit and best-of, **/spectate**, **/stats**, **/leaderboard**.

## Commands
`/hub` `/queue <kit> [ranked|unranked]` `/queue leave` `/ffa` `/duel <player> [kit] [bestof]` `/duel accept|deny <player>` `/spectate [player]` `/stats` `/leaderboard <kit|ffa>`
Ops: `/pvphub setlobby`, `/pvphub status`, and `/pvphub dev ...` (spawns fake players for testing).

## Install
Drop `pvphub-0.1.0.jar` and Fabric API into `mods/`. Set `gamemode=adventure` and `spawn-protection=0` in `server.properties`. On first start the lobby and arenas are built; settings are in `config/pvphub/config.json`, ratings in `config/pvphub/players.json`.

## Build
`./gradlew build` (Java 25) writes `build/libs/pvphub-0.1.0.jar`.

## Bot crowd (optional, needs VexBot)
With the VexBot mod installed, the lobby fills with `crowdBots` (default 100) bots that wander the lobby, cannot be hurt, and join and leave without any chat message. Every few seconds two of them take a free arena and fight an unrated duel you can spectate; `crowdDuels` (3) limits how many run at once and `crowdReserveArenas` (2) keeps arenas free for players. Set `crowdBots` to 0 in `config/pvphub/config.json` to turn it off. Raise `max-players` in `server.properties` (the bots count as players), and give the server a little extra tick headroom.

## Look and feel
Tab list header/footer (server name, region, online, duels, queued, FFA, Discord), rank tags from the Elo rank in the tab list and chat (best rank sorts first), and a sidebar. Change `serverName`, `region`, `address` and `discord` in `config/pvphub/config.json`. The crowd bots get rank tags, 1-100 ms pings and now and then say something in chat. The sidebar is the same for everyone.
