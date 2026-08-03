# Cryptonium plugin

A Minecraft (Paper) plugin for the Cryptonium play-to-earn server.

**Current version: Step 1 — starter plugin.** It adds Cryptonium as a real,
glowing custom item and a `/cryptonium give [amount]` command so you can see it
in-game. Everything else (ore, drop-on-death, vault, cashout) builds on this.

## How to turn this into a usable .jar

The code is here; it just needs to be "built" once. Two easy ways:

### Option A — Let GitHub build it for you (no software to install)
1. Create a free account at github.com.
2. Make a new repository and upload all these files to it.
3. Go to the **Actions** tab → run **"Build Cryptonium plugin"**.
4. When it finishes, download **Cryptonium-plugin** from the run's *Artifacts*.
   That download contains your ready-to-use `.jar`.

### Option B — Build it on your own PC
1. Install a free Java 21 JDK (Adoptium Temurin 21) and Apache Maven.
2. Open a terminal in this folder.
3. Run: `mvn package`
4. Your jar appears in the `target/` folder as `Cryptonium-0.1.0.jar`.

## How to run it
1. Get a Paper 1.21.x server (download Paper from papermc.io, or rent a host).
2. Put `Cryptonium-0.1.0.jar` in the server's `plugins/` folder.
3. Start the server, join the game, and type `/cryptonium give 5`.
