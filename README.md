# HeroesMini

Private Minecraft Java event plugin for Paper 1.21.x. Designed by Selim.

## Rules

- Every player starts with **3 Special Hearts** (shown as ❤❤❤ in the action bar above the XP bar).
- A PvP hit puts **both** attacker and victim into **Combat** for 20 s; every further hit refreshes the timer. The countdown is shown next to the hearts: `❤❤❤   ⚔ Combat 17s`.
- Dying **while in Combat** costs 1 Special Heart — whatever the final cause (fall, lava, mob). Dying outside Combat costs nothing.
- 0 Special Hearts = **eliminated** → spectator mode. Last player with hearts wins.
- On death nothing scatters: a **grave** (the player's head with a name tag) appears at the death spot, relocated to the nearest safe block if needed. **Anyone** can right-click it and loot it. The grave stays until it is empty. The dead player gets the coordinates in chat.
- Hearts, eliminations and graves survive server restarts (`plugins/HeroesMini/data.yml`, `graves.yml`).

## Admin commands (op only)

| Command | Effect |
|---|---|
| `/heroes start` | Start the event: everyone online gets 3 hearts. Players joining later get 3 too. |
| `/heroes stop` | Pause the event (spectators return to survival). |
| `/heroes reset` | Clear all hearts and graves, state back to idle. |
| `/heroes status` | Hearts and Combat state of every participant. |
| `/heroes hearts <player> <n>` | Set a player's Special Hearts. |
| `/heroes revive <player>` | Bring an eliminated player back with 1 heart. |
| `/heroes combat <player>` | Clear a stuck Combat timer. |
| `/heroes graves` | List graves with coordinates. `tp <id>`, `remove <id>`, `clear`. |

`config.yml`: `start-hearts`, `combat-seconds`, `grave-safe-radius`.

## Build

Requires nothing locally: every push to `main` builds the JAR in GitHub Actions
(Actions tab → latest run → artifact **HeroesMini**). A tag `v*` also creates a GitHub Release with the JAR.

Locally (Java 21): `./gradlew build` → `build/libs/HeroesMini-<version>.jar`.

## Run

1. Download a Paper 1.21.x server JAR from https://papermc.io/downloads/paper
2. Put `HeroesMini-*.jar` into the server's `plugins/` folder.
3. Start the server, op yourself, `/heroes start`.

## Test checklist

1. Two players, `/heroes start` → both see ❤❤❤.
2. A hits B once → both see `⚔ Combat 20s` counting down.
3. B jumps off a cliff within 20 s → B loses a heart, grave appears, coordinates in B's chat.
4. B waits 20 s after a hit, then jumps → no heart lost.
5. A right-clicks B's grave → GUI with B's items; take everything → grave disappears.
6. B loses 3rd heart → spectator, broadcast. If only A remains → A wins.
7. Restart the server → hearts and graves are still there.
8. Die in lava → grave is placed on the nearest safe block.

## License

MIT
