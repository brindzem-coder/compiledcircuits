# Visual state outlines — Part C

Implemented `client/ClientHoveredCircuit.java`, invoked once at END client tick by
`client/CircuitVisualClientEvents.java`. Hover is independent of explicit selection
and broken focus. Opening a screen, losing the selector/target, unloading the level
or disconnecting clears hover. Either hand may hold the selector.

The cache key includes level identity, dimension, target, membership revision and
physical topology revision. Compiled targets reuse the immutable network membership
set, including logical reservations outside loaded chunks, without BFS or copying.
Uncompiled searches read loaded actual block states and require bilateral wire
connectivity; compiled positions are excluded. Membership must be ready first.

BFS publishes only complete immutable results, processes at most 1024 nodes per
tick and accepts at most 50,000 unique positions. Overflow caches TOO_LARGE until
invalidation. Target/revision changes cancel pending jobs. Diagnostic getters expose
bfsStarted and bfsNodesThisTick.

Validation: `gradlew.bat build hoverCheck --offline --console=plain` succeeded.
`src/test/java/com/example/compiledcircuits/client/ClientHoveredCircuitTest.java`
passed 124 assertions for budgets, atomic publication, immutable results, cache
reuse, invalidation, compiled lookup and both sides of the 50,000-node limit.
These are graph/cache tests, not client-world integration tests. Actual world
connectivity, hand/screen transitions and chunk lifecycle were not tested in game
in this stage. Protocol remains 9. Part B Mixin registration is unchanged.

Part D outline rendering and Part F live membership mutation hooks remain later
stages; Part C alone does not add visible cyan outlines.
