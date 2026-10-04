# Stage 09 — membership capacity and bounded synchronization

Implemented 2026-10-03–2026-10-04 against stage-05 commit `d5d5cf8`. Scope: `fixes/09-membership-capacity.md`. Minecraft 1.20.1, Forge 47.4.10, Java 17.0.17+10, Gradle 8.8. Protocol version **14**; clients and server need matching builds. All seven manual in-game acceptance checks passed, as reported by the user. No user worlds were edited and no commit was created.

## Admission and accounting

`MembershipCapacity` owns derived per-dimension counts, immutable snapshot roots, revisions and an ordered network-ID index. Load rebuilds these without applying admission limits to old data. Add, grouped add, explicit replacement and prepared compilation publication share the capacity check. Scan uses the configured per-network ceiling; publication rechecks available dimension capacity before assigning the ID. Replacement uses current minus old plus new, including both dimensions. Failed admission does not consume IDs, change persistence or publish staged membership.

Active elements count regardless of signal, damage, folder or chunk availability. Each isolated raw record contributes the maximum of its position union, summed legacy role records and explicit element records. Separate raw claimants count separately even when their positions overlap; the transmitted blocked-position set is deduplicated. Unreadable accounting is UNKNOWN and blocks increases. Known-subset shrinking replacement and deletion remain possible. Arithmetic for counts, parts and encoded upper bounds is checked `long` arithmetic.

Configuration is `[membershipCapacity]` in the world's `serverconfig/compiledcircuits-server.toml`: `elementsPerNetwork` and `elementsPerDimension`. Both require a world/server restart. The policy is captured per SavedData session; hot reload is not supported. Test fixtures inject a bounded initial policy without writing Forge's live config.

## Limits and units

| Limit | Value |
|---|---:|
| New network elements | configured 1–50,000; default 50,000 |
| Reserved records per dimension | configured 1–1,000,000; default 1,000,000 |
| Logical snapshot records | 1,000,000 hard ceiling |
| Records per part | 4,096, counting active and blocked together |
| Physical application packet | at most 64 KiB |
| Parts per snapshot | at most 245 |
| Logical staging / shared server retention | 64 MiB encoded upper-bound budget |
| Conservative encoded estimate | 13 bytes per record + 1,024 bytes per part |
| Dimension / reason text | 256 / 128 characters; legal dimension syntax is ASCII |
| Active receiving sessions / queued refusals | 64 / 64 |
| Delivery per tick | at most 8 parts globally, 2 per player |
| Client application | at most 4,096 record/part transitions per tick |
| Staging timeout | 30 seconds without progress; 180 seconds total |
| ACK timeout | 600 server ticks after final part |
| Resync | 40 client ticks for absent/stale state; 200 for explicit UNKNOWN refusal |
| GUI list | at most 1,024 combined network/folder rows and 1 MiB conservative bytes |
| Command list pages | 20 entries |

The total staging lifetime accommodates the measured 64-recipient schedule under the global eight-part delivery budget. Identical duplicate parts do not extend idle time. Permanent OVER_CAPACITY does not automatically request repeated builds. Hard record, packet, byte and part ceilings were not increased.

## Old-world recovery and administration

Over-capacity data survives load/save intact. Dimension OVER_CAPACITY is a transport/admission condition, not physical damage or ownership conflict, and does not disable a healthy runtime. An old network exceeding only its individual admission limit remains visible in `/circuit list` with an OVER_CAPACITY label. `/circuit capacity` reports current dimension counts, ceilings, oversized networks, parts and estimated bytes.

`/circuit decompile <id>` supports recovery without relying on hover or a complete GUI list. `/circuit list [afterId]` uses the ordered index; `/circuit conflicts list [offset]` returns bounded pages. Rename and direct network moves no longer traverse the global damage list or reject solely because the selected network has many elements. Existing folder-validation rules still apply.

One exceptionally large existing network may enter an otherwise empty retirement queue; cleanup remains incremental and uses long work accounting. Further retirement must wait for available queue capacity. `/circuit conflicts remove <recordUUID>` queues an incremental rebuild of remaining raw reservations. The record and counts are published atomically only after preparation, with administrator permission checked throughout. One such job may be pending per SavedData. Save during preparation preserves the original complete records; restarting before publication requires requesting removal again.

Strict reductions can proceed while a dimension remains oversized. Revisions trigger a new full snapshot automatically after recovery, without relog. Other healthy dimensions continue syncing. GUI overflow produces an explicit unavailable response and directs the player to commands, never an apparently valid partial list.

## Synchronization and client lifecycle

Server construction captures immutable membership roots and advances by one element or network transition per SYNC work unit. Damage, highlight, membership and administrative reservation preparation share the stage-08 SYNC lane. Parts and completed batches are shared by viewers of the same dimension; slow receivers retain references rather than separate full copies. Unused acknowledged caches are evicted. Retention is checked before starting a builder; stale slow sessions time out and release references.

A connection/player/world nonce is echoed in every status and part. Snapshot IDs are monotonic within the server engine. Dimension, nonce and ID checks reject old-world and old-revision replies. Disconnect, respawn, dimension change and server stop clear the corresponding state. Build failures retain an explicit failed result and allow bounded retry; logging is limited per failed revision.

Client states distinguish UNKNOWN, STAGING, READY, STALE, OVER_CAPACITY and an explicit server UNKNOWN refusal. Complete snapshots publish atomically. Rejected/expired batches clear staging and invalidate lookups; UNKNOWN never means confidently uncompiled. Hover and outline consumers already gate on readiness. Empty successful snapshots remain valid READY. Packet counts and bytes are bounded before allocation, and duplicate positions, contradictory parts and inconsistent headers reject the batch.

## Verification and measured scope

See `baseline-results/stage09/verification.json` and `legacy-manifest.json` for final commands, results and immutable-input hashes. Routine checks: `gradlew.bat build visualChecks runGameTestServer --offline --console=plain`. The full-limit probe is explicit: `gradlew.bat membershipCapacityScaleCheck --offline --console=plain`. For copied real SavedData, populate `build/stage09-legacy/*.dat` and run `gradlew.bat runGameTestServer -Pstage09LegacyInputs=build/stage09-legacy --offline --console=plain`; its additional GameTest is registered only when that property is supplied.

Coverage includes limit−1/limit/limit+1 admission, checked overflow, bulk/cross-dimension replacement, competing prepared publications and rollback, load/save reconstruction, conservative raw accounting, gradual legacy reduction, automatic stream recovery, context/late-status rejection, malformed frames, maximum ID/header encoding, GUI guards, retirement and budgeted raw deletion. Existing damage, highlight, runtime, permissions, repair and compilation regressions also run.

The actual full-limit probe constructs 20 networks of 50,000 elements, encodes/decodes all 1,000,000 records and verifies every client membership set. A second pass shares the full snapshot among 64 simulated recipients, includes an unacknowledging receiver, checks global/per-peer limits, and drives a real client replica with a simulated 20-TPS clock through the long transfer. This is not 64 running Minecraft clients. Its heap measurement combines server data, snapshot fixtures and client maps in one 2-GiB JVM; the sum of memory-pool peaks is not a simultaneous live-heap measurement or an incremental networking allocation. Observed client-application pauses can exceed one frame; bounded record work does not guarantee a hard wall-clock deadline. No FPS/MSPT improvement is claimed.

Final verification on 2026-10-04: `build visualChecks runGameTestServer` with the copied-world property passed all **66 required GameTests**, including eight stage-09 cases and the optional three-file legacy check. Visual checks passed: hover 124, packet 28, cache 33, membership mutation/engine 87, priority 15; stage-07 assertions 185. All source/copy SHA-256 hashes still match the manifest. The existing chain fixture now explicitly selects/restores normal integrity quotas and waits for all 512 outputs HIGH within its unchanged deadline; final convergence took 25 ticks. Earlier failed/incomplete development runs are recorded in the verification notes, not counted as acceptance.

The final full-limit run passed with **1,000,000 records, 245 parts, 9,013,102 encoded bytes**, largest part 36,918 bytes. Client application used 245 ticks, 1,867.128 ms total measured work, maximum tick 66.322 ms. The 64-recipient pass sent 15,680 parts, at most eight globally and one per recipient per simulated tick in this schedule; it completed in 3,179 simulated ticks with 13,250,880 bytes of shared retained encoded upper bound. These are bounded-capacity checks and local measurements, subject to the limitations above.

## Ручні перевірки — успішно завершені

Виконувати в окремому тестовому світі або його копії. Для змін конфігу повністю вийти зі світу/зупинити сервер і відкрити його знову. Рахуються всі елементи: дроти, входи й виходи.

1. **Звичайний режим.** Із типовими лімітами скомпілювати й декомпілювати мережу, перевірити наведення до відкриття GUI, менеджер і порожній світ. Очікування: після синхронізації звична поведінка; порожній світ не показує capacity error.
2. **Межі допуску.** У конфігу встановити `elementsPerNetwork=16`, `elementsPerDimension=24`. Дві окремі мережі по 12 елементів (1 вхід + 10 дротів + 1 вихід) мають компілюватися. Третя мережа має отримати відмову через місткість виміру; окрема мережа із 17 елементів — через ліміт мережі. Перевірити `/circuit list` і `/circuit capacity`: попередні мережі та їхні ID не змінені, відхилена мережа не збережена.
3. **Незалежність від стану.** На скомпільованій мережі перемикати сигнал, пошкодити й відремонтувати дріт, перейменувати/перемістити мережу, відійти для вивантаження чанків і повернутися. Зарезервована кількість у `/circuit capacity` залишається незмінною.
4. **Старий завеликий світ.** Підняти ліміт виміру до 36, відкрити світ і створити три мережі по 12 елементів. Закрити світ; знизити ліміт виміру до 16, мережі — до 10; відкрити знову. Очікування: OVER_CAPACITY, усі три мережі збережені й доступні через команди; після звичайної перевірки runtime справні виходи працюють. Rename доступний. `/circuit decompile <id>` для першої мережі залишає 24 записи й OVER_CAPACITY; для другої — 12 записів і відновлення membership без relog. Остання стара мережа позначена як завелика за індивідуальним лімітом, але її дані не обрізані.
5. **Інший вимір.** Поки основний вимір OVER_CAPACITY, перевірити невелику допустиму мережу в іншому вимірі. Її синхронізація та наведення працюють. Координати іншого виміру не з'являються в поточному.
6. **Життєвий цикл.** Повторити вхід, respawn і швидкий перехід між вимірами. Старі контури/належність не мають повертатися після зміни світу; новий snapshot відновлюється автоматично. Короткий період без наведення до READY допустимий.
7. **Dedicated server.** Два клієнти в одному вимірі, третій — в іншому, усі з версією протоколу 14. Повторити відмову за лімітом і поступове відновлення. Усі клієнти відповідного виміру сходяться до нового стану; інший вимір продовжує працювати. Після перевірок повернути потрібні звичайні ліміти з перезапуском.

Підсумок ручної перевірки: користувач підтвердив проходження всіх семи сценаріїв, включно з dedicated server і трьома клієнтами. У сценарії 7 спочатку після перезапуску зникала щойно скомпільована C. Після повтору зі `save-all flush`, штатним `stop` і очікуванням завершення сервера мережа збереглася, а поведінка відповідала очікуванням. Збій не відтворився за штатного збереження; зміни коду збереження не знадобилися. Етап 09 завершено та готовий до коміту.
