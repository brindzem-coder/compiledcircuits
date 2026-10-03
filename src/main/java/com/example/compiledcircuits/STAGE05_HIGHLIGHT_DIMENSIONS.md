# Stage 05 — dimension-safe, bounded network highlighting

Implemented on 2026-10-03 against clean base commit `af7fc30` (stage 03). Scope: `fixes/05-highlight-dimensions.md`. Minecraft 1.20.1, Forge 47.4.10, Java 17.0.17+10, Gradle 8.8. Stage 05 is complete: automated verification and all seven manual client checks passed. Manual outcomes are user-reported, not agent-observed. No user-world edits and no agent-created commit.

## Behavior and covered entry points

Both `NetworkActionC2SPacket.HIGHLIGHT` and `NetworkBulkActionC2SPacket.HIGHLIGHT_NETWORKS` use `HighlightSync` and the stage-02 `NetworkOperations.HIGHLIGHT` admission service. All requested IDs are validated and deduplicated before collecting positions. A missing or isolated ID rejects the entire group. Valid networks in another dimension are skipped; the response reports their count. Only current-dimension networks contribute to the 50,000-position allowance. A wholly foreign selection produces a successful explicit empty result and clears the previous highlight. The manager still lists every dimension.

The GUI issues the contextual bulk request. There is no existing command that directly emits network-highlight geometry; the command paths were searched and no unprotected command sender remains. No new highlight command was introduced. The selector item's local connected-circuit selection remains available: beginning it or shift-clearing cancels pending explicit server highlighting. Its existing local scan limit is unchanged. It does not clear independent damage markers or membership caches.

Highlight admission now avoids the unrelated global metadata/broken-list traversal formerly shared with other actions. It checks at most 1,024 requested IDs, then uses immutable element counts before collecting positions. Existing spectator read access and stage-02 request/work quotas remain in force. An empty contextual bulk request is a constant-work cancellation, permitted even if the action quota has been spent; it never traverses geometry or mutates the world.

## Context, membership and rendering

Protocol **13** extends highlight action requests with a client context UUID, monotonically increasing request ID and dimension witness. The server derives the authoritative dimension from the player; a mismatching witness produces no geometry. Context is not an authorization credential. Context-free legacy highlight messages do not trigger a geometry response under the new protocol.

The client changes context when connection, ClientLevel or player identity changes, and clears on unload/logout. Request numbers do not reset on a same-dimension return. Incoming replies must match the captured connection and exact expected request/context/dimension. New requests and cancellation immediately clear old geometry and staging. Old successes, refusals and invalidations cannot overwrite a later request. READY, WAITING, ERROR and empty/no-request states are distinct; failures display a reason and require an explicit retry. A response closes the manager only if it is still the same screen that initiated the request.

`NetworkSavedData` owns a separate membership revision and a highlight invalidation observer. Registration increments the revision; removal/replacement also invalidates subscribers to the removed object. Each selection captures immutable network references and its revision. Changes to unrelated networks do not clear it. Removing/replacing a selected network cancels building, remaining delivery or already displayed geometry via a newer invalidation batch. Rename, folder moves and physical damage do not change geometry. Isolated saved records remain absent from active membership and cannot be highlighted as valid networks.

The client replica stages all categories separately and publishes only after every part and total validates. The published selection is bound to the actual ClientLevel object. Both the selection renderer and outline-priority consumer check that binding. Wire/input/output colors and damage-over-selection priority are preserved. Rendering skips unavailable client chunks and does not request them. Server builders read saved membership only; neither packet codecs nor highlight construction read blocks or acquire chunks.

## Bounded work and protocol

Each player has at most one current job/selection. A newer request replaces its queued data. Builders advance one element or network transition per work unit; they do not synchronously merge position sets. Each record is exactly nine encoded bytes: category byte plus packed BlockPos. Parts contain at most 3,000 records; real encoded packet size, including headers, is checked in codec tests. The decoder bounds the entire frame and payload before loops/large allocations. Client staging rejects duplicate positions (including contradictory categories), inconsistent headers, conflicting repeated parts, invalid totals, invalid roles, and timeouts. Identical repeated parts and reordered parts are supported.

| Limit | Value |
|---|---:|
| Raw request IDs | 1,024 before deduplication |
| Selected current-dimension positions | 50,000 |
| Positions per physical part | 3,000 |
| Encoded application frame | 32 KiB maximum |
| Parts / logical batch | 32 |
| Staging payload budget | 1 MiB; valid maximum selection uses 450,000 bytes |
| Active tracked player selections | 64 |
| Pending capacity-refusal recipients | 64, latest response per recipient |
| Highlight parts per server tick / per player | 8 / 2 |
| Client assembly steps per tick | 1,024 |
| Incomplete assembly idle timeout | 200 client ticks |
| Request/unfinished server job lifetime | 20,000 ticks |
| Dimension / response message characters | 256 / 256 |

The 50,000 cap reuses `OperationLimits.ELEMENTS`; it is not a new saved-world capacity policy. With 64 admitted selections, retained position payloads are at most 28,800,000 bytes plus one small buffer per builder and bounded headers/control packets. Collections, immutable membership references and transport buffers add overhead. Completed payload buffers are released after sending; membership ID/reference descriptors stay until replacement, disconnect/world change or server stop so displayed selections can be invalidated. Unacknowledged Netty/TCP buffering is outside this application staging figure.

Highlight work shares the existing `ServerWorkBudget.Lane.SYNC` allowance with damage synchronization. The coordinator alternates streams and uses an idle stream's turn for the other stream. It neither grants a second time budget nor lets one active stream consume every work turn. Each stream has its own revision and delivery caps: combined damage+highlight application delivery is at most 16 parts/server tick and four parts/player tick, still inside the common cooperative sync work/time allowance. Encoding or allocation can overrun one unit; this is not a hard real-time bound.

There is no automatic retry loop after highlight errors. Selecting fewer networks or issuing a fresh request after the problem is resolved starts a clean assembly. Capacity refusals are queued under the same highlight send cap. Player connection and server-level identity are checked before continuing work/delivery; disconnect and server stop release references.

## Automated verification

Tests use the isolated `run-stage7-tests` directory. Production codecs, server engine and client replica are connected in-process; this is not a claim that three actual graphical clients were launched.

Six new GameTests cover:

1. Same coordinates in Overworld/Nether, single/bulk filtering, duplicate IDs, foreign oversized networks filtered before position traversal, atomic missing-ID rejection and no loaded-chunk growth for remote geometry.
2. A → B with late A parts/refusals, explicit empty results, cancellation during transfer, and return to the same dimension with a new context.
3. Removal before building finishes, during delivery and after display; replacement invalidation; rename/damage preserving membership geometry; disconnect and stop cleanup.
4. 49,999 / 50,000 / 50,001-position boundaries and actual packet/staging measurements.
5. Reordered/repeated parts, conflicting payloads and revision headers, conflicting position categories, idle/request timeout, malformed size rejection before allocation and recovery on a new request.
6. Twelve independent clients, per-client/global part caps, one client's cancellation without affecting others, and payload-buffer release.

`baseline-results/stage05-first-tests.log`: `build visualChecks runGameTestServer --offline --console=plain` passed, **57 required GameTests**. Existing stage-03 damage and stage-08 work-budget checks passed alongside the new selection tests. The final run after lifecycle/priority review also passed build, visualChecks and **all 57 required GameTests** (`baseline-results/stage05-final.log`, 1m 30s). Source `git diff --check` passed.

Maximum-size measurement (same result in the initial and final runs): 50,000 positions → 17 physical parts, **451,241 encoded application bytes**, **450,000 peak client staging payload bytes**, 146 simulated engine/client ticks. The old format's three counts plus packed positions would occupy 400,012 application bytes for this fixture. The new format adds category/context/validation overhead (about 12.8%) to obtain bounded atomic delivery; no traffic reduction or FPS improvement is claimed. Simulated ticks use the deterministic test allowance of 512 engine steps and 1,024 client assembly steps, not a real network latency measurement.

## Completed manual acceptance

The user confirmed that all seven checks supplied in chat passed exactly as expected, including the dedicated-server scenario. The checklist below records that report; controlled late-packet ordering and exact capacity boundaries remain supported by the automated tests rather than inferred from visual observation.

1. Highlight a current-dimension compiled network: wire/input/output colors remain correct, including a damaged member where the damage marker keeps priority. Highlight works after closing the manager.
2. Have networks at the same coordinates in Overworld and Nether. Highlight one foreign network from the manager: old contours clear and a skipped-network explanation appears. Highlight a mixed group: only current-dimension networks appear, with the correct skipped count.
3. Start a large highlight, then request another network or shift-clear with the selector. Only the latest request may appear; cancelled contours must not return. Local selector use must likewise supersede a pending server result.
4. With a selection active/pending, travel Overworld → Nether → Overworld, respawn and reconnect (also to a different world/server). Nothing from the old context may reappear; issue a fresh selection to show geometry again.
5. Decompile a selected network during/after delivery: its selection clears with an invalidation explanation. Rename/move and ordinary wire damage preserve selection geometry; damage markers remain independent.
6. Large selection: preparation completed, the result appeared together rather than as a partially ready scheme, and a subsequent smaller selection worked.
7. Dedicated server with multiple players in different dimensions: selections stayed independent; one player's clear/change did not affect others, and a fresh request worked after reconnect.

Actual rendering/colors and multiplayer lifecycle behavior are now covered by the user's successful manual report. Maximum protocol-bound heap behavior has not been profiled; the manual large-selection check does not imply a 50,000-block in-game test. Stage 05 is **verified and complete** for the documented scope. Stages 09, 10 and 11 were not implemented here.
