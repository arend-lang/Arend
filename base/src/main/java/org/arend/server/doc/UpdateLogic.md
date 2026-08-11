The logic for partial resetting and updating in the Arend Server (specifically within the IntelliJ IDEA plugin context) follows a pull-based incremental workflow. It combines module-level invalidation with definition-level dependency tracking to minimize the amount of re-resolution and re-typechecking.

### 1. Trigger: PSI and Document Changes
The process begins in the IntelliJ IDEA plugin.
*   **Highlighting Pass:** When a user edits an Arend file, IntelliJ triggers an `ArendHighlightingPass`.
*   **Resolution:** The highlighting pass immediately calls `ArendChecker.resolveModules` to update name resolution for the current file.
*   **Background Typechecking:** If background typechecking is enabled, the highlighting pass then schedules a background task via `RunnerService.runChecker(module)`.

### 2. Pull-Based Module Updates
When `ArendCheckerImpl` (the server-side implementation) starts resolution or typechecking, it ensures all required modules are up-to-date:
*   **Dependency Calculation:** It traverses imports in the module being checked.
*   **Update Request:** For each module, it calls `myServer.getRequester().requestModuleUpdate`.
*   **IntelliJ Callback:** `ArendServerRequesterImpl` (in the plugin) receives this request, finds the `ArendFile` for the module, and calls `server.updateModule(file.modificationStamp, ...)`.

### 3. Server-Side Module Update (`ArendServerImpl.updateModule`)
The server handles the update in several stages:
*   **Timestamp Check:** It only updates if the provided `modificationStamp` is newer than what it currently holds.
*   **Module-Level Invalidation:**
    *   It calls `resetReverseDependencies`, which recursively traverses all modules that import the updated module (directly or transitively).
    *   It calls `groupData.clearResolved()` on these modules, marking them as needing re-resolution.
*   **Group Update and Referable Preservation:**
    *   `GroupData.updateGroup` compares the new version of the module's `ConcreteGroup` (built from PSI) with the previous one.
    *   If a definition is "similar" to an old one (e.g., same name and structure), it **reuses the existing `TCDefReferable`**. This is critical because all dependencies in the system are tracked via these unique `TCDefReferable` instances.

### 4. Incremental Definition Invalidation (`DependencyCollector`)
After updating the module's structure, the server performs name resolution:
*   **Comparison:** In `ArendCheckerImpl.resolveModules`, it compares the newly resolved definitions with their previous versions using `DefinitionData.compare`.
*   **Dependency Trigger:** If a definition `D` has changed in a way that might affect others (e.g., its signature changed), it calls `myServer.getDependencyCollector().update(D)`.
*   **Minimal Component Computation:**
    *   `DependencyCollector` maintains a graph of reverse dependencies between definitions (`myReverseDependencies`), populated during previous typechecking runs.
    *   The `update` method recursively finds all definitions that depend on `D`.
    *   It marks all these dependent definitions as needing re-typechecking by setting their `typechecked` state to `null`.

### 5. Partial Typechecking
Finally, the actual typechecking happens:
*   **Ordering:** The server uses an `Ordering` component to determine the correct order of definitions to check (respecting dependencies).
*   **Filtered Check:** It only invokes the typechecker for definitions that are either new or have had their `typechecked` state invalidated (set to `null`) in the previous steps.
*   **Result Persistence:** Successful typechecking results and new dependency information are stored back in the server's global state and the `DependencyCollector`.

### Summary of the Workflow
1.  **IDEA Edit** -> `ArendHighlightingPass` -> `RunnerService`.
2.  **Server `updateModule`** -> Invalidate dependent **modules** -> Reuse `TCDefReferable`s for unchanged-looking definitions.
3.  **Server `resolveModules`** -> Compare concrete definitions -> If changed, use `DependencyCollector` to invalidate dependent **definitions**.
4.  **Server `typecheck`** -> Re-typecheck only the "minimal component" of invalidated definitions in dependency order.
