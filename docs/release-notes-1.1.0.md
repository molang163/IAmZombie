# I Am Zombie? 1.1.0

Version 1.1.0 replaces the old COMMON configuration authority with explicit server and client authorities. Existing registry IDs, saved attachment formats, translation keys, and resource paths remain compatible with 1.0.3. The Minecraft/NeoForge range retained from 1.0.3 is documented below.

## Configuration authority

- Gameplay configuration is now a NeoForge SERVER config. A connecting client receives the server-owned values it needs for display or prediction, but synchronization is not treated as a security boundary: gameplay results, movement limits, damage, cooldowns, and probabilities remain server-computed or server-validated.
- Heartbeat controls and the local Herobrine jolt vignette live in `config/iamzombieq-preferences-client.toml`.
- The two appearance options remain in `config/iamzombieq-client.toml`; migration does not rewrite that file.
- `config/iamzombieq-common.toml` is no longer registered as a live config. It remains read-only migration input for supported 1.0.3 upgrades.

## Upgrading from 1.0.3

The complete 1.0.3 migrator remains available throughout the 1.x line so a supported future 1.x release can still be installed directly over 1.0.3.

- Migration runs before NeoForge can generate a missing destination file.
- An existing SERVER or preferences destination is never overwritten and does not cause the legacy file to be read. It must already be complete, valid, and correction-stable; otherwise startup stops with the affected paths, reason, and recovery steps.
- A missing applicable destination is projected from the old COMMON file, written through the permanent-lock/journal protocol, reopened and validated, and only then marked complete. `ATOMIC_MOVE` has no non-atomic fallback.
- SERVER migration evidence is bound to the actual global or per-world destination. One world's marker cannot prove another world complete.
- The old COMMON file and the appearance file are left unchanged.

If migration stops with an F1 error, do not delete or replace lock, journal, marker, backup, initial, or stage files blindly. Follow the paths and recovery instructions in the error. Parent/ancestor namespace replacement by non-cooperating processes is outside the 1.x guarantee; any observable binding change still stops migration.

## Connections and spider mounts

Authority readiness is negotiated from the configuration protocol and schema fingerprint, not from exact mod-version string equality. State is scoped to one connection epoch and cleared on disconnect or server switch. Client reads of server-owned values fail closed until that epoch is ready.

Spider riding additionally has a server-side movement envelope and server-authoritative passenger admission. The passenger repair path covers cross-dimension tracking and reconnect convergence without trusting client configuration as authority.

## Compatibility boundary

The reviewed 1.0.3 public JVM shape of `IAmZombieConfig` is retained: class, fields, `SPEC`, helper descriptors, modifiers, and generic signatures remain linkable. This is a binary-shape promise only. `IAmZombieConfig` is an internal/deprecated facade, not a stable configuration API:

- `SPEC` now refers to the SERVER spec;
- heartbeat fields refer to the preferences CLIENT spec;
- there is no COMMON shadow copy;
- the old 1.0.3 `ConfigValue.get`, `getRaw`, `set`, and `save` lifecycle semantics are not preserved. In particular, CLIENT-backed heartbeat aliases are not loaded on a dedicated server and operations that require a loaded CLIENT config fail by NeoForge's normal precondition.

Add-ons should use the stable `api/*` surface instead of treating `IAmZombieConfig` as a cross-side live configuration API.

## Downgrading

Downgrading to 1.0.3 leaves the new SERVER/preferences files and migration evidence on disk, but 1.0.3 ignores them and reads its unchanged COMMON file. Consequently, values edited only in the 1.1 files do not become 1.0.3 COMMON values. Re-upgrading does not overwrite existing valid 1.1 destinations.

## Supported versions

Version 1.1.0 targets Minecraft 26.2 and declares NeoForge compatibility from `26.2.0.12-beta` (inclusive) through `26.2.1-alpha` (exclusive). `26.2.0.25-beta` is the primary tested baseline.
