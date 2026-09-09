# Visual outlines — Part F

NetworkSavedData now marks membership dimensions dirty after addNetwork,
successful removeNetwork and validated removeNetworks. Replacement also marks
its previous dimension. Dirty dimensions are a set, so bulk removals coalesce.
NBT load still populates the map directly and sends no mutation notifications.

Audited call paths: NetworkCompiler and CircuitCommands add through addNetwork;
command and GUI single decompile use removeNetwork; GUI bulk uses removeNetworks.
Existing CompiledElementSync flushes at server END tick, builds once per affected
viewed dimension, shares packets with all its players, and sends up to four parts
per player per tick. Login/dimension/respawn requests and stop cleanup remain active.
Empty snapshots remove the last network on the client. Atomic client commit bumps
membership revision and invalidates hover; no physical rediscovery is required.

No protocol change (9), visual setBlock calls, integrity/repair policy changes or
focus mutations. Full-dimension snapshot limits remain one million entries,
4096 entries per part, 64 MiB assembly and 30 second assembly timeout.

Regression harness: src/test/java/com/example/compiledcircuits/networking/CompiledElementMutationTest.java
via membershipMutationCheck. Tests mutation coalescing, rejection without dirtying,
bulk deduplication, cross-dimension replacement, silent NBT load and empty snapshot.
Actual multiplayer packet delivery and GUI/command visual transitions need in-game
verification. Clear explicit selection when checking pink after decompile.
