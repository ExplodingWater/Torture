# Torture

**Torture** is a specialized security and moderation plugin for Paper/Spigot servers (Java 17+). It allows administrators to flag specific usernames for automatic restriction, inventory wipes, and debilitating effects upon successful login via AuthMe.

## Features
* **AuthMe Integration:** Restrictions trigger only after the player successfully logs in.
* **Persistent Restrictions:** Automatically applies max-level Slowness, Blindness, Weakness, Hunger, and Mining Fatigue.
* **Immediate & Persistent Wipes:** The target's inventory is cleared immediately when torture is enabled, and **every time** they log in while flagged.
* **Offline Reservation:** If you flag a player while they are offline, the punishment (and wipe) will be waiting for them the moment they join.
* **Death Loop:** If a tortured player dies, their effects and inventory wipe are re-applied instantly upon respawn.

## Usage Tutorial

### The Command
The plugin revolves around a single, powerful command:

`/torture <player> <true|false>`

**Permission:** `torture.admin`

### Arguments Explained
1. **`<player>`**: The exact username of the target you want to punish.
2. **`<true|false>`**:
    * **`true`**: ENABLES torture. The player is blinded, slowed, and their inventory is wiped.
    * **`false`**: DISABLES torture. The player is released, and effects are cleared (if they are online).

### In-Game Examples

#### 1. Punishing a Player
To start punishing a player named `GrieferJoe`:
/torture GrieferJoe true

* **If they are ONLINE:** You will hear a heavy iron door sound. Their inventory vanishes instantly, and they are hit with blindness and slowness.
    * *Chat Output:* `[Torture] Player punished: GrieferJoe (ON)`
* **If they are OFFLINE:** The database is updated. The moment `GrieferJoe` joins the server next, his inventory will be wiped and effects applied.
    * *Chat Output:* `Target not online but effect reserved upon target joining.`

#### 2. Releasing a Player
To stop the punishment:
/torture GrieferJoe false

* The effects are immediately removed from the player.
* *Chat Output:* `[Torture] Player released: GrieferJoe (OFF)`

## Installation
1. Ensure you have [AuthMe Reloaded](https://github.com/AuthMe/AuthMeReloaded) installed.
2. Drop `Torture-1.0.jar` into your `/plugins` folder.
3. Restart the server.

## Configuration
The `data.yml` file is used to store the active states of players. You generally do not need to edit this manually, as the command handles everything.