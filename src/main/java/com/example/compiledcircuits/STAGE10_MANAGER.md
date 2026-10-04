# Stage 10 — manager refactoring

Base: `f910325` (stage 09). Scope: `fixes/10-manager-screen.md`. Completed; automated verification passed and all seven manual acceptance scenarios passed as reported by the user. Teleport and the planned GUI boundary redesign are separate work.

## Observed behavior before changes

| Scenario | Existing contract |
|---|---|
| Tree | Root `/`, expanded by default; siblings sorted by case-insensitive name, stable input order for ties; missing parent falls back to root. |
| Network panel | Only current folder, ascending numeric ID. |
| Search | Trimmed case-insensitive name substring; folders first, then name and ID. Both / Networks / Folders filter. Normal click exits search and navigates. |
| Selection | Folder and network IDs are separate namespaces. Plain click selects one on release; dragging an already selected row preserves its group. Shift-click toggles; Shift-drag paints traversed rows. No Ctrl/range-anchor selection or context menu exists. |
| Hidden selection | Editing query preserves selected IDs, including hidden rows; bulk acts on the entire selection. Changing filter clears selection. Root cannot be selected for mutation. |
| Navigation | Expands root and ancestors, clears search and scrolls target into view. |
| Actions | Highlight, rename, drag move, decompile; new/rename/delete folder. No compile button or confirmation dialogs in this manager. Compile-name dialog is separate. |
| Broken mode | Composite network+element keys; Shift painting; first Select All expands one network, repeated Select All selects the whole list. Repair/decompile apply to selected networks, not only selected elements. |
| Status | Network rows display last received effective ON/OFF, masked by known broken IDs. Broken view disables actions while damage is unconfirmed and displays synchronization status. |
| Updates | Current code clears all normal selection on list refresh. A list reply can replace a text dialog or reopen a closed screen. Broken/list snapshots are independent. These lifecycle defects will be corrected explicitly and tested. |
| Text dialog | Target IDs for rename are captured, create-folder currently reads live current folder. Resize resets typed text; Esc closes instead of returning to parent. These defects will be corrected separately from extraction. |
| Geometry | Rows start at 92; heights folder 18/network 31/search 30/broken 36. Only full rows are interactive. Broken wheel region starts at 55 although rows start at 92. Folder names lack clipping; row fill has inset/gaps relative to hit regions. Preserve baseline placement; record geometry discrepancies for the next requested GUI change. |

## Ownership and verification

- `ManagerModel`: copied immutable server records, ID indexes, existing FolderNode tree, visible rows, search, separate damage projection. Equal snapshots rebuild nothing; folders rebuild only when their records change. Iterative tree traversal and explicit invalid-tree warning handle cycles/orphans without rewriting server records.
- `ManagerState`: selection namespaces, broken composite keys, current folder, expansion, search/focus/scroll and transient pointer gestures. Hidden selection remains selected; only deleted IDs are removed on updates. Deleted current folder falls back to nearest surviving ancestor, then root.
- `ManagerActions`: captured one-shot edit targets, immutable bulk targets and injectable transport, no optimistic mutations. Identical requests have a two-second duplicate guard, released by list refresh; this is not a correlated success acknowledgement. Server remains authoritative. Context invalidation/close disables requests.
- `ManagerLayout`: shared row extents, complete-row counts, hit indexes and scroll bounds. A viewport smaller than one row now correctly has zero rows. Baseline panel positions and known clipping/wheel-region issues are retained for the separately requested geometry fix.
- `ManagerRenderer`: read-only draw calls and visual helpers. No packets, selection mutations, tree rebuilds or model updates during render.
- `NetworkManagerScreen`: widgets, input gesture sampling, component coordination and lifecycle. `NetworkTextEditScreen` retains typed text/caret/focus on resize; Save is one-shot; Esc/Cancel return to the parent without a mutation.

## Explicit lifecycle fixes and protocol contract

Snapshot updates preserve surviving typed IDs, including after rename/move; they cancel transient drags, never retarget a row index. The original unconditional selection reset is removed. Create-folder captures its parent ID at dialog creation. Manager updates continue into the parent of an open edit dialog without replacing the dialog. Closing/disconnecting/changing world invalidates the session; no static manager reference or persistent listener is introduced.

Ordinary `NetworkListS2CPacket` replies update an already open manager (including its child dialog) and never open one. Explicit `/circuit gui` opening uses the existing `OpenNetworkManagerAtS2CPacket` with ID 0, and explicit navigation keeps its real target ID. Both handlers reject another connection. Packet encoding and protocol version 14 are unchanged.

An open manager or its dialog requests `circuit gui refresh` every 40 client ticks through the action transport. This read-only command uses the existing GUI authorization and bounded list builder; it exposes another player's rename/move/delete without a permanent subscription or unbounded broadcast. Refresh stops when the session closes. This is a two-second polling interval at 20 TPS, not a guarantee of immediate updates. Server refusals remain server messages; no invented result acknowledgement or success label is added.

The search header now shows the total selection including hidden rows, making bulk targets explicit. Compile remains outside the manager. Teleport, new confirmation dialogs, Ctrl/anchor range selection and context menus are not introduced.

## Automated evidence

`gradlew.bat build visualChecks runGameTestServer -Pstage09LegacyInputs=build/stage09-legacy --offline --console=plain` passed: **66 required GameTests**, including the optional copied-NBT check; hover 124, packet 28, membership cache 33, mutation/engine 87, visual priority 15, stage-07 assertions 185, and **78 manager-component assertions**. Log: `baseline-results/stage10-verification.log`.

The manager tests instantiate no Screen/renderer/Minecraft instance. They cover tree/name/ID order, name filtering, hidden selection, namespace collision, Shift toggling/paint idempotence, new objects with stable IDs, deletion/ancestor fallback, malformed/deep trees, composite damage selection, captured dialog targets, cancel, duplicate/late actions, context invalidation, bounded refresh and row boundaries/scroll.

After the final renderer/layout delegation and unused-helper cleanup, `gradlew.bat build managerCheck --offline --console=plain` also passed; final log: `baseline-results/stage10-final.log`. Structured evidence: `baseline-results/stage10/verification.json`.

A permitted 1,024-record model (256 folders + 768 networks) built in 4.800 ms in the full verification run. 10,000 unchanged model/layout observations took 3.964 ms and caused zero tree/row/search rebuilds. This is a local CPU probe, not a rendered frame/FPS benchmark or a before/after speedup claim. The agent did not perform screenshot-based visual acceptance or a real two-player GUI run; the user subsequently confirmed all seven manual scenarios, including the two-player run, passed.

## Ручні перевірки — успішно завершені

Використовуй копію тестового світу з кількома скомпільованими мережами. Клієнти й сервер мають використовувати поточну збірку. Ліміти місткості залиш звичайними. Записи `/ccperf` для цих перевірок не потрібні.

1. **Дерево й переміщення.** Відкрий `/circuit gui`. Створи папки `Alpha`, `Beta` та вкладену `Child` у `Alpha`. Перетягни одну мережу в `Child`. Згорни й розгорни `Alpha`, перейди в root і назад. Очікування: мережа лишається у своїй папці; папки впорядковані за назвою, мережі — за ID; розкриття зберігається при фонових оновленнях.
2. **Вибір і пошук.** Вибери дві мережі через Shift-клік, третю додай проведенням миші з Shift. Відпусти мишу й Shift. Введи частину назви, яка приховає одну з вибраних мереж. Очікування: прихована мережа залишається вибраною, загальний лічильник це показує; Highlight застосовується до всього вибору. Перемикання Both/Networks/Folders очищає вибір. Звичайний клік по результату очищає пошук і відкриває потрібну папку/мережу.
3. **Діалог і розмір.** Вибери одну мережу, натисни Rename, введи нову назву, але не зберігай. Зміни розмір вікна. Очікування: текст і положення курсора збереглися. Натисни Esc: повертає в менеджер, назва не змінена. Повтори та натисни Save: перейменовується лише обрана мережа, вона залишається вибраною. Так само перевір New/Rename папки.
4. **Прокрутка й масштаб.** Використай список, який не вміщується на екран; перевір папки, мережі, результати пошуку та Broken. Прокрути вниз, зміни розмір вікна й GUI scale, вибери останній видимий рядок. Очікування: прокрутка не виходить за список, натискання відповідає видимому рядку, порожній залишок унизу не вибирає невидимий запис. Окремо занотуй уже відомі розбіжності рамок/обрізання для наступного GUI-виправлення: у цьому етапі їх дизайн не змінювався.
5. **Пошкодження.** Пошкодь по кілька дротів у двох мережах. У Broken вибери один елемент першої мережі; перше Select All вибирає її пошкодження, друге — весь список. Перевір Shift-вибір, Open Network, Repair Network. Очікування: ремонт застосовується до вибраних мереж, відремонтовані рядки зникають; звичайний менеджер продовжує працювати.
6. **Закриття та повторне відкриття.** Виконай Rename або переміщення й одразу закрий менеджер. Почекай кілька секунд: він не має відкритися сам. Повтори open/close кілька разів; одна дія має давати один результат. Знову відкрий `/circuit gui`: актуальні дані доступні. Перепідключися та перевір повторне відкриття.
7. **Два гравці.** Перший відкриває Rename мережі A й вводить текст, не зберігаючи. Другий перейменовує/переміщує A; зачекайте понад дві секунди. У першого діалог і текст залишаються; Save стосується тієї ж A. Повторіть, але другий декомпілює A. Після оновлення Save у першого не повинен змінити сусідню мережу; має бути відмова/повідомлення про недоступну ціль. Окремо видаліть поточну папку другого гравця: менеджер переходить до чинного предка або root без crash.

Статус: етап 10 завершено. Користувач підтвердив, що всі сім описаних ручних сценаріїв пройшли як очікувалося, включно з перевіркою двох гравців. Автоматичні перевірки також пройдено. Зміни меж GUI/обрізання тексту та кнопка телепортації залишаються окремими наступними завданнями. Коміт не створено.

