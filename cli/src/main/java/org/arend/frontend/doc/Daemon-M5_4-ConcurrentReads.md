# M5.4 — Concurrent Reads in the Arend Daemon

Status: **pending** (planned, not implemented). Today every client request runs on a
single `arend-daemon-worker` thread; a `-ss` query that arrives while `-ai` is still
typechecking queues behind it even though the typecheck wouldn't have minded sharing the
CPU. This file is the plan for letting genuinely read-only ops run in parallel.

## Goal

Allow these ops to run concurrently:

- `-ss` (symbol search)
- `-fu` (find usages)
- `-ch` (class hierarchy)
- `-sc` (referable scope)
- `-ps` (proof search)
- `--daemon-status` / `--daemon-ping` (already concurrent — they don't touch the worker)

Keep these strictly serialized on the existing worker:

- the initial bootstrap (`-ai` on first daemon start)
- `refresh` op (bootstrap argv replay)
- internal refresh fired by `SourceWatcher`
- the regular `cli` path when it carries `-ai`, `-t`, or any flag that mutates server
  state

The rule of thumb: **anything that calls `checker.typecheck(...)`, `loadBinaryCache`,
`persist`, or mutates `ctx.failedDefinitions` / `ctx.moduleResults` is a writer; anything
else is potentially a reader.**

## What needs auditing first

A reader pool only works if reads can't corrupt or be corrupted by an in-flight writer.
Today nothing has been audited for parallel access; this is the prerequisite work for
M5.4.

### Hot spots

1. **`ArendServerImpl`** (`base/src/main/java/org/arend/server/impl/`). Already uses
   `synchronized (myServer)` blocks at a few call sites (e.g. `ArendCheckerImpl`
   line 100). Need to check: are the unsynchronized read paths (`getModules`,
   `getRawGroup`, `getReferableScope`, `getCheckerFor`) safe to call while a writer
   holds the monitor? If the writer is the only one mutating state, readers under a
   read lock would be safe. If readers ALSO mutate (e.g. lazy-init a cache), they need
   their own write protection.

2. **`ArendCheckerImpl`**. `resolveAll` and `typecheck` BOTH mutate server state
   (populate `myDependencies`, `myConcreteProvider`; call back into
   `requestModuleUpdate` which loads source files into the server). So `-fu`, `-ch`,
   `-sc`, `-ps` are NOT purely-read — they call `resolveAll` first. The audit needs to
   decide whether to:
   - skip the `resolveAll` for read ops (rely on the warm context already having
     everything resolved at bootstrap), or
   - allow `resolveAll` under a shared monitor (multiple readers serializing on
     `synchronized (myServer)` may still parallelize cheaper non-server work).

3. **`LibraryManager`**. Owns the `myLibraries` map. Mutated by `loadLibrary` / `unload`
   (writer-only paths). Reads (`getLibrary`, iteration) need to be safe under concurrent
   read; today the map is a plain `HashMap`. Either swap to `ConcurrentHashMap` or
   require all reads to happen under the server monitor.

4. **`CliServerRequester`**. `myBinaryCacheLoaded` is a `HashSet` mutated by
   `loadBinaryCache` (writer). Read access via `getBinaryCacheLoaded` is unsynchronized.
   Reads concurrent with writes would NPE or miss entries; need
   `ConcurrentHashMap.newKeySet()` or synchronized access.

5. **`CommandContext`**. Has heavily mutable per-request bookkeeping:
   `moduleResults`, `failedDefinitions`, `bufferedErrors`, `requestedModules`,
   `exitWithError`, `bufferErrors`. `CliDispatcher.resetPerCommandState` clears these
   at the start of every request — works fine for the current single-worker model;
   breaks immediately if two requests share the same ctx. **Per-request ctx clones
   (aliasing server / libraryManager / requester / requestedLibraries) are the cleanest
   fix.**

6. **`SymbolIndex`** (the on-disk index used by `-ss`/`-fu`/`-ch`/`-sc`). Refreshed via
   `idx.refresh(lib, server, noCache)`. Concurrent refresh calls could double-write the
   index file. Mitigation: ensure index refresh only happens at bootstrap / refresh
   time (writer path), and read-ops just *use* the index without refreshing.

7. **System out/err redirection (`StreamRedirector`)**. Already per-thread via
   `ThreadLocal`. Multiple workers can attach simultaneously without conflict — good.

### Empirical check before the audit

Before reading code, **stress-test the current daemon**: fire 4–8 parallel `-ss` queries
at it (or mixed `-ss` + `-fu`) and look for:

- crashes / NPEs in `daemon.log`
- mismatched outputs (one client's frames going to another)
- deadlocks (status stuck on BUSY past the expected duration)
- wrong results (e.g. 0 matches when there should be 133)

If today's daemon happens to survive parallel reads, the audit can focus on what's
actually unsafe rather than auditing everything defensively. If it doesn't, the crash
trace points directly at the unsafe path.

## Implementation sketch

Phase 0: stress-test. (Above.)

Phase 1: ctx cloning. Add `CommandContext.forSubRequest()` that returns a new ctx
with shared server/libraryManager/requester/requestedLibraries and fresh empty
bookkeeping collections. Used for every queued read-op work item. Worker keeps using
the original ctx for writer ops (its mutations survive across requests by design — the
`-ai` warm cache).

Phase 2: server-side categorization. In `DaemonServer.handleRequest`, peek at the args
for the `cli` op and route to a new `readQueue` if the parsed CLI flags fit the
"pure read" set above; otherwise stay on the worker queue. (We already re-parse args
in `CliDispatcher.run`; the categorization can reuse that parse, or do a fast pre-parse
of the first flag.)

Phase 3: read pool. A small `ThreadPoolExecutor` with N=`Runtime.availableProcessors()`
threads that pulls from `readQueue`. Each thread:

- takes a `ReadWorkItem(args, replyTo, requestId)`
- builds a sub-ctx from the warm ctx
- attaches `StreamRedirector` for that thread
- calls `Dispatch.execute(subCtx, parsedCmdLine)`
- detaches, sends `done` frame
- recycles back to the queue

Phase 4: write barrier. When a writer item is dequeued by the main worker, drain all
in-flight reads first (e.g. `readPool.awaitTermination` on a per-batch latch). Without
this, a typecheck that mutates server state mid-read could give the read inconsistent
data. Implementation choice: a single `ReentrantReadWriteLock` on `DaemonServer` —
readers hold the read lock for their duration, writer acquires the write lock.

## What "done" looks like

- A `--daemon-status` invocation reports `readActive: N` alongside `state` /
  `queueDepth`.
- 4 parallel `-ss` queries return in ~the time of 1, modulo JVM warmup.
- A writer (refresh) issued while 4 reads are in flight waits for them to finish, then
  proceeds; reads issued during the writer wait until it finishes.
- No new test regressions; existing single-threaded behavior unchanged when only one
  client at a time hits the daemon.

## Why this is the last sub-milestone

Concurrent reads make the daemon faster under multi-agent load (e.g. several Claude
sub-agents querying the same daemon). They don't unlock any new capability the way
M5.1 (refresh), M5.2 (cancel), and M5.3 (source watch) did. The right time to do this
is when single-worker queuing becomes a felt bottleneck, because the audit cost is
real and the empirical signal from a stress test is more useful than a defensive
top-down read.
