# Refuse writes while GnuCash desktop holds the lock

Status: Accepted, 2026-11-04

## Context

When desktop opens the SQL book it inserts a row into `gnclock (hostname, pid)`; the sample dump still holds `Leopard / 8300`. Desktop does **not** reload rows written by other clients while open, so a parallel write is invisible until reopen and can conflict with, or be overwritten by, desktop's own saves. piecash refuses to open a locked book unless told `open_if_lock`.

A lock row can also be stale: desktop crashed, or the laptop went to sleep and was never closed.

## Decision

- Reads are always allowed.
- Writes are refused while `gnclock` has any row. The UI shows "Locked by HOST (PID n)" and disables write buttons.
- The server re-checks inside the **same DB transaction** as the write (`LOCK TABLE gnclock IN SHARE MODE`, then abort if any row exists), so a lock taken between page load and save still blocks. A desktop that opens the book during the write waits for the commit. A row lock (`FOR SHARE`) could not stop a new row being inserted.
- A **Clear lock** button deletes the `gnclock` rows after a confirmation that names the host and PID, so the user knows which desktop they are overriding.

## Consequences

- The normal flow is: close desktop, then enter on the phone. Entries made while desktop is open must wait.
- Clearing a live lock is the user's explicit choice; if desktop is really running it will not see the phone's writes and may conflict.
- The app's DB role needs `DELETE` on `gnclock` (ADR-0006).
