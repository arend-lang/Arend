# Working with the Arend Daemon and Source Auto-Refresh

This is the day-to-day workflow once a daemon is running for a library. Auto-refresh is
always on when the daemon is started — there's no flag to disable it; if the watcher
fails to register (rare: only when no `FileSourceLibrary` resolves), the daemon prints
`[DAEMON][watch] disabled (no source roots resolved)` and otherwise runs the same.

## One-time setup per shell

Start the daemon for the library you're working on:

```sh
arend -d -L <libdir> <libRef> -ai
```

This blocks until the initial `-ai` pipeline (resolve, typecheck, write `.sig` mirrors,
refresh the symbol index) finishes. On `arend-lib` that's ~10–90 seconds depending on
whether the `.arc` cache is warm. When the daemon prints `[DAEMON] READY`, you can hit
Ctrl-C in the foreground (the daemon is already detached — it won't die) or just open a
second terminal.

After this, every `arend ...` invocation against the same library auto-routes through
the daemon. You don't pass any extra flag — the client looks up `.arend/daemon.lock`
under the library root, checks the PID + library hash, and connects to the socket.

If you ever need the in-process behavior (e.g. to compare against a clean JVM), pass
`--no-daemon`:

```sh
arend --no-daemon -L .. arend-lib -ss "monoid"
```

## The edit loop

1. Open the source you're editing in your usual editor.

2. Save the file. The watcher sees the `.ard` modification within a few ms. After 300ms
   of quiet (no further saves), the daemon enqueues an internal refresh.

3. The refresh re-runs the bootstrap pipeline (`-ai`) against the warm context. Source
   files with advanced mtimes are re-parsed and re-typechecked; everything else stays
   cached. On `arend-lib` this is typically 2–3 seconds.

4. **Output of the auto-refresh goes to `<libdir>/.arend/daemon.log`, not your
   terminal.** Tail the log in a side terminal if you want live error reporting:

   ```sh
   tail -f /home/you/.../arend-lib/.arend/daemon.log
   ```

   What you'll see per auto-refresh:

   ```
   [DAEMON][watch] refresh triggered (source edit)
   ...
   --- Done (1958ms) ---
   [INFO] Persisted 1 module(s) (332 up-to-date)
   arend-lib: indexed (1 stale module re-parsed).
   ```

   If your edit broke typechecking, the same block will include a
   `[ERROR] Module:line:col: <message>` line.

5. Run queries against the now-fresh context:

   ```sh
   arend -L .. arend-lib -ss "monoid"
   arend -L .. arend-lib -fu "Algebra.Monoid:Monoid"
   arend -L .. arend-lib -sc "Algebra.Monoid:Monoid"
   ```

   These return in well under a second because no library load happens; the result
   reflects whatever the last refresh wrote.

## Coalescing and queue behavior

- **Save bursts.** Rapid sequential saves within the 300ms debounce window count as one
  refresh. Saving a directory of 50 files in `git checkout` produces one refresh that
  re-typechecks the affected modules.
- **Refresh skipping.** While a refresh is running or one is already queued, additional
  watcher events are dropped (the watcher logs nothing extra and the daemon's
  `requestInternalRefresh` no-ops). The next debounce tick after the in-flight refresh
  finishes catches up any saves that happened during the busy window. You won't end up
  with a backlog of refreshes.
- **Client queries during refresh.** A query sent while a refresh is BUSY queues on the
  worker; it runs after the refresh completes. `--daemon-status` will report
  `state: BUSY, queueDepth: 1` while you're waiting. Today the daemon has a single
  worker — see `Daemon-M5_4-ConcurrentReads.md` for the plan to lift that constraint.

## Cancelling work

Press Ctrl-C in the client terminal. The client's shutdown hook sends a `cancel` op
with its task id; the worker observes this at the next checkpoint inside the
typechecker and bails out with exit code 130. The daemon goes back to IDLE
immediately; subsequent queries are unaffected.

Cancel applies to anything routed through the daemon worker:

- `-ai` / typecheck (`-t`) — fast, may not have time to observe cancellation if very
  short
- `-ps` proof search — observes cancellation
- `--daemon-refresh` (manual refresh) — observes cancellation

**Auto-refreshes themselves are not cancellable from a client** — there's no client to
Ctrl-C. They run to completion or until the daemon shuts down.

## Manual escape hatches

- `arend --daemon-refresh <libRef>` — force a refresh now (useful after a `git
  checkout` that updated mtimes the watcher might have missed, or just to confirm
  things are clean).
- `arend --daemon-status <libRef>` — show state / queueDepth / uptime / currentTaskId.
- `arend --daemon-stop <libRef>` — clean shutdown. Removes lock file and socket.
- `arend --no-daemon ...` — bypass the daemon for one invocation.

## When the watcher doesn't help

- **External tools that don't touch `.ard`** (build scripts, dependency upgrades that
  swap out `.arc` files): the watcher only listens for `.ard` events. Use
  `--daemon-refresh` if you change non-`.ard` files that the daemon cares about, or
  restart the daemon.
- **A typo in the daemon's bootstrap args.** Watcher fires refresh against the same
  args used at startup. If the bootstrap was wrong, every refresh will be wrong the
  same way. Restart with corrected args (`--daemon-stop` then `-d` again).
- **A daemon serving a stale lib version.** The lock file stores a `libraryHash`; if
  the library config (`arend.yaml`) changes (deps, version), clients will refuse to
  route to the old daemon and run in-process. Restart the daemon.

## Failure modes worth knowing

- **`daemon.log` grows unbounded.** No rotation today. If you're keeping a daemon up for
  days, `truncate -s 0 daemon.log` or restart.
- **`Cannot run program "setsid"` on macOS.** `DaemonStart` skips `setsid` on Windows
  but assumes it's present on Unix. macOS doesn't ship it by default — install
  `util-linux` from Homebrew or run from a session that won't SIGHUP the child.
- **Permission denied on the socket.** The UDS lives under `.arend/daemon.sock` with
  default umask. Different user accounts on the same machine can't share a daemon —
  start one per user.
