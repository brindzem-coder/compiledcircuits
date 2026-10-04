# Stage 11 — cached wire shapes

Base commit: `125e23d` (stage 10). Implemented and automatically verified on 2026-10-04. Scope: `fixes/11-wire-shapes.md`. All four manual client scenarios passed as reported by the user; stage 11 is complete.

## Implementation

`BasicWireBlock` geometry depends only on its six boolean state properties. A private static final array holds all 64 outline shapes, initialized after the unchanged geometric primitives. Mapping: NORTH=1, SOUTH=2, EAST=4, WEST=8, UP=16, DOWN=32. Construction preserves the old primitive coordinates and join order. Mask zero uses CENTER. The table performs 192 joins once during class initialization; it has no world, chunk, registry or client-singleton dependencies.

`getShape` now reads six properties, computes a mask and returns the existing array entry. There are no joins, temporary collections or lazy mutations on that path. Cached shapes are not externally exposed for mutation. Collision remains `Shapes.empty()`. Placement, neighbor update, connection rules, endpoint shapes, world renderers, block registration, saved data and protocol remain unchanged. No migration or load/unload invalidation is needed.

## Verification

Command: `gradlew.bat build visualChecks runGameTestServer -Pstage09LegacyInputs=build/stage09-legacy --offline --console=plain`.

Result: **BUILD SUCCESSFUL; all 68 required GameTests passed**. This includes two new stage-11 tests and the optional copied legacy-NBT test. Existing checks also passed: hover 124, manager 78, packet 28, membership cache 33, mutation/engine 87, priority 15 and stage-07 assertions 185. Log: `baseline-results/stage11-verification.log`; structured summary: `baseline-results/stage11/verification.json`.

`WireShapeGameTests` reproduces the old primitives and branch rules independently of the new cache builder. It compares occupied volume using `!Shapes.joinIsNotEmpty(expected, actual, BooleanOp.NOT_SAME)`. The test confirms the XOR truth table and detects a hollow shape with the same outer bounds, so bounding-box equality alone cannot pass. All 64 states, the center, each single direction and all directions together pass. Repeated calls at different positions and with empty/entity contexts return the same instance; a null world/position/context is never accessed. Direct block and BlockState shape/collision paths are covered.

The neighbor test places/removes basic wire, input endpoint and output endpoint in each of six directions, verifies the updated BlockState/outline and return to CENTER, and checks that stone does not connect. Test blocks are restored in `finally`. Tests run in the isolated Forge GameTest world, not user worlds. Dedicated/server class loading passed.

A warmed direct-call microbenchmark uses the same 64-state sequence for both routes, 4,096 warm-up calls per route and four measured rounds alternating route order. For 131,072 calls per route: old joins **475.358 ms**, cached lookup **27.058 ms**. Static table construction happens before measurement and is excluded. No timing threshold decides test success. Zero steady-state joins is confirmed by inspection of the lookup implementation, not a profiler counter. These timings do not establish FPS/TPS improvement: Minecraft may cache shapes itself, and this benchmark measures direct method calls only.

## Ручні перевірки — успішно завершені

Використай тестовий творчий світ. Для перевірки форми дроти спочатку не компілюй; `/ccperf` не потрібен.

1. **Окремий дріт.** Постав один дріт на зручній висоті. Якщо потрібен тимчасовий опорний блок — потім прибери його. Наведися на центральну частину з різних боків, зверху й знизу. Очікування: звична форма центра та контур, без зайвих відгалужень; наведення на порожній простір поруч із центром не повинно вибирати дріт.
2. **Шість напрямків.** До цього дроту по черзі додавай сусідній дріт на північ, південь, схід, захід, зверху та знизу. Після кожного додавання наведися на відгалуження, потім прибери сусіда й знову перевір центр. Очікування: відгалуження й контур з’являються з потрібного боку та зникають після видалення сусіда без перезаходу. Наприкінці постав усіх шість сусідів одночасно та перевір кілька видимих відгалужень.
3. **Endpoints і звичайні блоки.** Заміни одного із сусідніх дротів спочатку входом, потім виходом; між замінами перевіряй наведення. Потім постав на його місце камінь. Очікування: до входу й виходу є звичне з’єднання, до каменю його немає; старе відгалуження не залишається.
4. **Прохід і робота мережі.** У творчому режимі, не в spectator, пройди або пролетіть крізь дріт із відгалуженнями. Він не має стати фізичною перешкодою. На невеликій звичній схемі виконай компіляцію, перемкни важіль і декомпілюй. Очікування: сигнал і звичне підсвічування працюють як раніше.

Користувач підтвердив проходження всіх чотирьох ручних сценаріїв як очікувалося. Етап 11 завершено. Завершальний інтеграційний прогін усього початкового плану — окремий наступний крок. Зміни GUI та телепортація залишаються відкладеними до завершення плану.
