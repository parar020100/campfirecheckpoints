# Enhanced Campfire Checkpoints

This is a fork of fbicat's Campfire Checkpoints  

Original project:  
[Modrinth](https://modrinth.com/plugin/campfire-checkpoints)  
[GitHub](https://github.com/fbikat/campfirecheckpoints)  

(distributed with Apache 2.0 license)

---

## Description

Right-click any lit campfire to set it as your checkpoint. When you die within range, you'll respawn at your nearest campfire instead of world spawn.

---

## Original features

🔥 Simple Setup – Right-click a lit campfire to create a checkpoint  
⚰️ Smart Respawning – Respawn at the closest checkpoint within configurable radius  
🔄 Auto-Extinguish – Campfires extinguish after use (configurable) (can relight with flint & steel, or extinguish yourself with a shovel)  
📍 Multiple Checkpoints – Set several checkpoints across your world  
🛡️ Override Protection – Confirmation required when replacing nearby checkpoints  
🔔 Real-time Notifications – Get alerts when checkpoints are lit, extinguished, or destroyed  

## Fork features

🥖 Restored the ability to cook (`require-empty-hand-or-sneak` option)  
🔉 A sound for respawning on campfires  

⚓️ Support for respawn anchors to use campfire mechanics  
- 🔅 can be enabled to work in all dimentions (configurable)  
- 🛌 anchors no longer override bed spawnpoints  
- ♾️ unlimited by distance  
- 👤 1 anchor per player  


⚙️ More configurable options:  
- 📍 per-dimention toggles for campfires and soul campfires (separately)  
- 🔘 separate radius options for regular and soul campfires (soul campfires can work with more distance)  
- 📏 minimal distance between campfires **can be different from the radius** (exists for both regular and soul campfires)  
- 🚫 an option to disable the `/cc delete` command, forcing players to break checkpoints manually  

🔧 Fixes and behaviour tweaks:  
- `override-confirmation-timeout` option can be set to 0, forbidding to override existing non-broken campfires  
- errors due to missing / invalid / extinguished ones fixed (now checks all checkpoints before respawn)  


## Commands  
`/cc list` - View all your checkpoints  
`/cc delete <index>` - Remove a checkpoint  
`/cc info` - Show plugin info and stats  
`/cc reload` - Reload configuration  

---

## Configuration

`enable-regular-overworld`,  
`enable-regular-nether`,  
`enable-regular-end` - whether campfire checkpoints work in dimensions  

`enable-soul-overworld`,  
`enable-soul-nether`,  
`enable-soul-end` - same for soul campfires  

`radius`,
`soul-campfire-radius` - how far from death to search for checkpoints  
`min-distance`,
`soul-campfire-min-distance` - minimal distance between checkpoints  

`respawn-priority` - priority when both bed / anchor spawn and campfire checkpoint are within radius (`checkpoint` / `bed` / `closest`)  

`require-empty-hand-or-sneak` - requires sneaking or empty hand to set checkpoints, **allows cooking**  

`extinguish-on-respawn` - whether respawn extinguishes the campfire  
`override-confirmation-timeout` - seconds to confirm replacing a checkpoint (can be 0 to disable overriding)  
`max-checkpoints-per-player` - limit checkpoint count (0 = unlimited)  

`sound-on-set`,  
`sound-on-respawn` - sound effects  

`allow-delete-command` - toggles the `/cc delete` command

**Respawn anchors:**

`enable-respawn-anchors` - override respawn anchors to function like checkpoints

`enable-respawn-anchors-overworld`,  
`enable-respawn-anchors-nether`,  
`enable-respawn-anchors-end` - per-dimension overrides for respawn anchors  

`vanilla-respawn-anchors-in-nether` - vanilla respawn anchor mechanics in the nether


## Permissions

`campfirecheckpoints.use` - Create and use checkpoints - Everyone  
`campfirecheckpoints.reload` - Reload plugin config - OP  
`campfirecheckpoints.admin` - Full admin access - OP  


## Contacts and support
**original creator**: fbikitty (Discord)  
**fork developer**: parar020100 (Discord, Telegram)  