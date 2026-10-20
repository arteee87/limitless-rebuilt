 # Limitless Rebuilt

A maintained port of the mod **limitless** by [auoeke](https://github.com/auoeke/limitless)

Original repo is not available, the last released jar was extracted & analyzed by AI.

Mod features:
     
     raises the enchantment level limit to 2147483647;
     allows items to be reenchanted (configurable; enabled by default);
     normalizes anvil enchantment cost to take the same amount of experience as a player with that exact level;
     makes enchantment cost for players with levels above 30 the same as for players with level 30 (configurable);
     allows enchantments in enchantment tables to be revealed (disabled by default);
     makes the enchanting power of all blocks configurable (only through the JSON configuration);
     treats other mods' bookshelves as enchanting blocks in addition to the default bookshelf;
     disables survival mode anvil level limit (or caps the cost at a configurable value, enabled by default);
     makes anvil repair cost constant instead of exponential (always on);
     disables bookshelf limit and increases range to 64 meters horizontally and vertically (configurable);
     allows treasure enchantments to be acquired at enchanting tables (configurable; enabled by default); and
     shows the enchantment effects in the tooltip (configurable, enabled by default).

# Requirements:

[Fabric API](https://modrinth.com/mod/fabric-api)

[Cloth Config](https://modrinth.com/mod/cloth-config)

[Mod Menu](https://modrinth.com/mod/modmenu) - optional


# Downloads

[Built jars are at the releases page](https://github.com/arteee87/limitless-rebuilt/releases)

# Source code

Check the branches (branch name = mc version).

# Building

Requires JDK 25.

     git clone https://github.com/arteee87/limitless-rebuilt.git
     cd limitless-rebuilt
     git switch <branch>
     ./gradlew build

The jar lands in build/libs/. 

# Reporting issues

Open one at https://github.com/arteee87/limitless-rebuilt/issues
If the game starts, open mod settings (in modmenu), enable debug (all options), attempt to reproduce the issue on your end and close the game.
Attach the ./minecraft/logs/latest.log in the issue and describe what you've got vs what you've expected to happen.
If the game fails to start: attach the crash report from .minecraft/crash-reports/ instead.

# License

Licensed under LGPL-3.0-only as per the original repo.
