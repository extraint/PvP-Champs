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
