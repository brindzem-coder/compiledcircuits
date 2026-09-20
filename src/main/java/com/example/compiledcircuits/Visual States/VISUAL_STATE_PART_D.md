# Visual state outlines — Part D

Changes:
- `client/CircuitStateOutlineRenderer.java`: client-only AFTER_TRANSLUCENT_BLOCKS
  subscriber, private buffer and cached line RenderType. POSITION_COLOR_NORMAL with
  the vanilla lines shader, 1.5 px lines, LEQUAL depth, color-only writes, blending.
  RenderType state teardown restores the normal world-stage baseline, including
  enabled depth testing. No global buffer flush or per-block draw call.
- `client/ClientCircuitBlockIndex.java`: deferred stale-position removal after
  traversal, guarded by active level identity; invalidates topology for hover.

One physical-index traversal uses chunk/section distance and frustum rejection,
then position rejection and loaded chunk actual-state verification. No discovery
scan or BFS runs in rendering. Missing/non-circuit physical elements are skipped.
Unknown membership suppresses outlines. Compiled elements have no normal outline;
hover is cyan (0.15, 0.85, 1.00, 0.85), uncompiled is pink (1.00, 0.30, 0.72, 0.70).
Geometry is twelve cube edges at 0.002..0.998. Both colors share one batch.
Counters: getOutlinedPositions(), getDrawCalls() (zero or one for this renderer).

Validation: build and hoverCheck succeeded, with 124 hover/cache assertions.
Visual occlusion, line thickness, Fabulous mode, interaction with other overlays,
and GPU/frame costs have not been tested in game. Future opaque full-cube circuit
blocks may require an outward epsilon; current small wires/endpoints use inset.

Manual checks: place uncompiled wires without a selector (pink), hover a connected
group in either hand (cyan), move crosshair away (pink), hide behind an opaque wall
(occluded), and open a GUI (hover clears, normal pink remains). For compiled hover,
use a network present at login; live compile/decompile sync is Part F.

Part E priority exclusions and focus regression remain next; existing explicit and
broken overlays can overlap these outlines until then. Protocol remains 9, Mixin
target/config and Stage 7 state persistence are unchanged.
