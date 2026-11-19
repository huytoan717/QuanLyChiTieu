# Transaction details, editing and deletion

Goal: Open a saved transaction from Home or History, inspect every field, edit with the existing form, and delete after confirmation. Apply changes locally immediately and retain pending cloud mutations across restarts.

Architecture: Keep SQLite as the UI source of truth. Separate immutable draft creation from explicit updates/deletes. A shared transaction repository observes SQLite and synchronizes pending changes through Firestore. Persist deletion markers so stale cloud snapshots cannot restore deleted records. Reuse the existing transaction form for editing, preserving historical wallet/category values.

Constraints: Preserve existing data through a versioned database migration. Keep current dependencies and AGP configuration. Do not implement wallet management, user account management, or unrelated features. Existing wallet balances remain outside this feature; transaction-derived totals must refresh. Test cloud behavior through an isolated fake remote, never by deleting user cloud data.

- [x] Storage: migrate transactions.db from version 1; implement explicit update, deletion markers, pending revisions, remote merging, and all-record reads. Test reopening, migration, validation, stale snapshots and acknowledgements.
- [x] Repository: serialize local mutations; emit updated lists to active screens; queue Firestore writes/deletes, show pending/error states and retry. Use one shared transaction stream and close UI subscriptions.
- [x] Details: new Activity/layout with type, amount, category, source/destination wallet, date, note, budget exclusion, sync state, edit button and destructive confirmation. Handle missing/deleted IDs and prevent repeated actions.
- [x] Editing: reuse AddTransactionActivity with an ID extra; prefill all values and retain legacy wallet/category labels; preserve draft on rotation; save by updating the same ID; run spending-cap checks once using the selected date and excluding the original record.
- [x] Integration: open Details from both Home and History. Refresh transaction-derived summaries and budgets including zero after deleting the last matching expense; keep selected report period and list filter.
- [x] Verification: unit tests, isolated SQLite/repository/UI instrumentation, debug/release build and Lint. Visually inspect details/edit/confirmation with normal and large text. Record results and limitations here.

Evidence folder: `.codex/transaction-crud-2026-11-16/`. The workspace has no Git metadata; source/configuration files are copied and hashed there before edits.

Verification completed 2026-11-16: debug/release builds succeeded; 9 unit and 36 instrumentation tests passed. The transfer selection regression also passed two isolated reruns. Refined screenshot checks passed separately at font scales 1.0 and 2.0, with screenshots inspected. Debug/release Lint each reports 0 errors, 255 warnings and 6 hints. Firebase synchronization was tested with a fake remote; live server and physical-device/TalkBack verification remain unperformed. Full evidence and usage: docs/transaction-crud-verification-2026-11-16.md.
