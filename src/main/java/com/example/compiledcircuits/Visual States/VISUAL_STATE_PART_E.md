# Visual outlines — Part E

Changed: client/CircuitVisualPriority.java (read-only per-render exclusions),
client/CircuitStateOutlineRenderer.java (broken/selection exclusions),
client/NetworkSelectionRenderer.java (broken exclusion in all three color loops),
client/ClientSelectionEvents.java (normal selection cleanup on active level unload/logout).

Broken reservations union raw current-dimension positions with current-dimension
focus positions. Focus-hidden broken positions remain reserved. Explicit selection
uses its existing three sets, matching the existing renderer activation. Hover data
is preserved under overlaps. Existing colors, geometry and focus-close logic remain
unchanged. No protocol, Mixin or BlockState changes.

Regression harness: src/test/java/com/example/compiledcircuits/client/CircuitVisualPriorityTest.java
and Gradle visualPriorityCheck. Covers raw/focused union, dimension guards, all three
selection types, clearing selection and read-only behavior across two GUI closes.
Actual in-game overlap rendering and dimension transition require manual testing.
Live compile/decompile membership updates remain Part F.
