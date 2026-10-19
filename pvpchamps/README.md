# PvP Champs (Vexbot extension)

Simulates PvP Champs on a Vexbot server: fight roster bots in best-of-N matches, watch bot-vs-bot matches, or run an 8-player bracket. Bots get extra pro moves (pearl close-in, pop punish, mend reset, bait retreat). Uses the Relay Spectator mod for the broadcast HUD and round breakdown.

Install `vexbot-3.0.14mc26.2.jar`, `relay-spectator-0.1.0.jar` and `pvpchamps-0.1.0.jar` on the server, and Relay Spectator on the client.

Commands (`/pvpchamps ...`): `roster`, `kits`, `challenge <opp> [kit] [bestof]`, `watch <a> <b> [kit]`, `tournament start|status|stop`, `moves on|off|stats`.
Kits: spear_mace, mace, cart, netherite_op, diamond_smp, netherite_smp. Roster lives in `config/pvpchamps/roster.json`. Opponent names and ratings are fictional.
