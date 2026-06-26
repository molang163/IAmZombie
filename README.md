# I Am Zombie?

You aren't fighting the zombie, you are the zombie. Once you turn, the survival rulebook flips: sunlight burns you, villagers run from you, beds explode under you, and you get by on rotten flesh, a wall-climbing spider, and a coffin.

## Start and world rules

- The first time you become a zombie you get a small starting stash: **8** rotten flesh by default (configurable, range 0–64).
- Peaceful difficulty is locked, its button is hidden, and the world is forced to Easy or higher.
- Creative still runs these rules; only Spectator is exempt.

## Five forms

There are five zombie forms: Normal, Drowned, Husk, Zombified Piglin, and Giant.

- **Innate armor**: Normal / Drowned / Zombified Piglin have 2 points each, Husk has 4, Giant has 0.
- **Baby form**: half scale and +50% movement speed; after going hungry long enough it grows into an adult.

Per-form perks:

- **Husk**: a melee hit applies Hunger II for 15 seconds; does not burn in sunlight.
- **Drowned**: full mining speed underwater, built-in night vision, never drowns.
- **Zombified Piglin**: permanent fire resistance, gold gear loses durability at 1/4 the normal rate, and nearby zombified piglins defend you when you are hit.
- Normal and Drowned burn in sunlight; Husk and Zombified Piglin do not. A pumpkin or a damageable helmet blocks the sun; the disguise mask does not.

## How you die decides your evolution

The way you die determines what you become:

- Starve → revert to baby form
- Drown → Drowned (a Husk that drowns reverts to Normal)
- Burned to death by sun in a desert → Husk
- Die in Nether lava → Zombified Piglin

Each form gives a one-time reward the **first** time it is unlocked: Drowned gives a trident, Husk gives a batch of desert drops (2–4 randomized stacks), Zombified Piglin gives an enchanted golden sword; each also grants a matching advancement.

Evolving heals roughly half your max health and tops up a little food. An ordinary death (not an evolution) reverts you to the normal adult form, keeps your inventory and XP, and respawns you in place.

## Combat

- Damage scales with difficulty: Peaceful / Easy ×1.10, Normal ×1.25, Hard ×1.50.
- Breaking a wooden door empty-handed is 3× faster.
- Whatever you hit hits back.
- On Hard with mob spawning enabled, a hurt zombie player can call same-form reinforcements (with anti-farm decay of −0.05 per successful spawn, up to 50 spawn-position attempts per hit; the Giant gets no reinforcements).

## Coffin and sleep

- Beds explode for zombie players: default power **5.0** and they start fire (both configurable).
- The coffin has 12 wood variants, crafted as "planks planks planks / white wool, rotten flesh, white wool / planks planks planks". The first time you turn, all 12 recipes unlock.
- At night a coffin only sets your respawn point.
- During the day, with no hostiles nearby, you can actually sleep: a multi-tick nap that advances while the screen gradually darkens.
- Sleep runs a per-dimension vote using the sleep-skip percentage gamerule; once it passes, it skips to night.
- Your respawn point is set the moment you lie down.
- Taking damage, a hostile showing up nearby (re-checked once per second), or the coffin being destroyed all interrupt sleep.

## Disguise

- Disguise mask: rotten flesh + leather ×2 + string, shapeless, **15** durability.
- Wearing it makes villagers and wandering traders trade with you; each successful trade costs 1 durability.
- The mask **only** gates trading. It does not fool combat mobs: iron golems and the like still attack you.
- The mask does not block the sun.
- Without a disguise, villagers and wandering traders flee at 8 blocks.
- Killing a villager has a chance to turn it into a zombie villager (keeping its profession and trades).

## Relations with mobs

- **Always attack you (all forms)**: Iron Golem, Snow Golem, Zoglin, Goat, Creeper. The Endermite is grouped with Creeper and also attacks all forms.
- **Conditional**: Trader Llama attacks every form except Zombified Piglin; Axolotl attacks only the Drowned form.
- **Left to vanilla, never force-targeted onto you**: bosses like the Warden and Wither; Enderman and Polar Bear only retaliate when provoked.
- Undead kin and passive animals leave you alone by default; Drowned do not friendly-fire each other and have their own rally behavior.
- Your tamed spider and the big zombie you ride never attack you.

## Food

- Rotten flesh and raw meat are safe to eat, but rotten flesh **no longer grants any buff** (no Strength, no Saturation).
- Cooked human food gives Hunger II (360 ticks) plus Nausea (240 ticks); sweets (cookie / cake / pumpkin pie) add Slowness on top.
- Spider eye gives Night Vision (900 ticks / 45 s by default); glow berries give a short Night Vision (120 ticks / 6 s).
- Cod and salmon give Water Breathing (400 ticks by default); tropical fish gives 300 ticks.
- Raw rabbit gives Speed (160 ticks) and Saturation (80 ticks).
- Pufferfish gives Absorption and Regeneration (durations and levels configurable).
- Super rotten flesh gives Strength (900 ticks) and Saturation (160 ticks), and also restores a baby to adult.
- The golden apple is nerfed: it only gives Absorption (1200 ticks of Absorption I by default, not healing) and tacks on a Hunger penalty, unlike vanilla healing.
- A poisonous potato grants a random positive effect: Speed, Haste, or Luck.
- Some foods (pufferfish, spider eye, poisonous potato, super rotten flesh) can be eaten even with a full hunger bar.
- Debuffs you already had before eating (vanilla Hunger / Nausea / Poison, plus golden-apple Regeneration / Absorption / Resistance / Fire Resistance) are preserved and restored afterward.

## Mounts

- **Spider**: tame it **progressively** by feeding rotten flesh, spider eye, or super rotten flesh (100 points to tame; rotten flesh +20, spider eye +35, super rotten flesh +60). Feeding also heals it (rotten flesh 4, spider eye 6, super rotten flesh 10). Once tamed it is rideable and climbs walls, ridden speed defaults to 0.30 (configurable), and it does not despawn.
- **Baby-only mounts**: a baby zombie can right-click a chicken empty-handed to ride it, or ride an adult big zombie.
- **Big zombie**: mount with an empty-handed right-click, control with WASD, with native jumping (it mirrors your jump key). While ridden it auto-attacks: your last target → your last attacker → a proximity scan (villager > golem > monster). A big zombie you just provoked cannot be mounted.
- **Undead horses**: a wild zombie or skeleton horse is auto-tamed and owned when a zombie player interacts with it; rotten flesh heals 4, super rotten flesh heals 10.
- Killing a normal horse as a zombie player has a difficulty-based chance to turn it into a zombie horse; killing a normal nautilus likewise turns it into a rideable zombie nautilus.

## Giant

Kill the vanilla Giant in Creative to transform into the Giant form.

- 100 health, 6× scale (1.0 base + 5.0 modifier).
- 27-block block reach, 18-block entity reach, 3.6-block step height, +3.0 safe-fall bonus.
- A punch deals 55 fixed damage (not difficulty-scaled).
- Stomp aura: 10 damage per second within 5 blocks (every 20 ticks).
- Walking crushes soft blocks along the way (body box widened 2.0 horizontally and vertically, hardness limit 2.0, up to 256 blocks per tick) and it is immune to suffocation.
- Left-click swing: a 17×17×17 region, up to 200 blocks per swing (hardness limit 5.0), 25-tick cooldown.
- It cannot break containers, bedrock, or obsidian.
- The Giant gets no reinforcements.

## Misc

- 14 advancements in total.
- Configurable options include: starting rotten flesh, bed explosion power and whether it starts fire, spider mount speed, and the client skin.

## License

Released under the MIT License. See [LICENSE](LICENSE).
