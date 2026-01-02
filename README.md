# Torture

**Torture** is a security and moderation (and screwing with) plugin for Paper/Spigot servers (Java 17+). It allows administrators to flag specific usernames for automatic restriction (and screwing with) upon successful login via AuthMe.

## Features
* **AuthMe Integration:** Actions trigger only after the player successfully logs in.
* **Persistent Restrictions:** Automatically applies max-level Slowness, Blindness, and Weakness.
* **One-Time Wipe:** Specific "default targets" have their inventories, armor, and off-hand items cleared upon their first login.
* **Logging:** Broadcasts the coordinates of flagged players to the console upon entry.
* **Data Persistence:** Targets and wipe-states are saved to a `data.yml` file.

## Commands & Permissions
| Command | Description | Permission |
| :--- | :--- | :--- |
| `/torture <player> <true|false>` | Toggle restrictions for a user. | `torture.admin` |

## Installation
1. Ensure you have [AuthMe Reloaded](https://github.com/AuthMe/AuthMeReloaded) installed.
2. Drop `TortureWatcher.jar` into your `/plugins` folder.
3. Restart the server.

## Configuration
Modify `data.yml` to manually manage the list of flagged UUIDs/names and check who has already been wiped.
