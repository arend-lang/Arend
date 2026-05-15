# Testing ARC Persistence (Serialization / Deserialization)

This document describes the methodology for testing and debugging the binary
round-trip (serialize → deserialize) of Arend core definitions to `.arc` files.

## Overview

The Arend typechecker produces in-memory `Definition` objects (function bodies,
data types, class hierarchies, etc.).  These are persisted to `.arc` files via
protobuf-based serialization (`ExpressionSerialization`, `DefinitionSerialization`)
and loaded back via the corresponding deserialization classes.  A correct
round-trip means the loaded definitions are structurally identical to the
originals.

## Test infrastructure

### Round-trip test

**File:** `src/test/java/org/arend/library/ArendLibRoundTripTest.java`

The test has four phases:

1. **Phase 1 — Typecheck:** Load arend-lib sources and typecheck all (or
   selected) modules via `ArendServerImpl`.
2. **Phase 2 — Serialize:** Persist every module to `arend-lib/bin/` using
   `FileBinarySource.persist()`.
3. **Phase 3 — Deserialize:** Create a fresh `ArendServerImpl` and load every
   `.arc` file back using `FileBinarySource.load()`.
4. **Phase 4 — Compare:** For each module, run two checks:
   - **Core check** (`CoreDefinitionChecker`): Re-type-checks every loaded
     definition.  Catches structural corruption (wrong types, unbound variables,
     malformed elimination trees).
   - **Pretty-print comparison** (`ToAbstractVisitor`): Converts both the
     original and loaded definitions to concrete syntax and compares the
     strings.  Catches naming/rendering differences.

### Running the test

```bash
# Full library (all 333 modules, ~12 minutes)
./gradlew roundTripTest

# Specific modules (much faster, 20-50 seconds)
./gradlew roundTripTest -Darend.roundtrip.modules="Algebra.Ring,Data.Array,Paths"
```

The test is excluded from the normal `./gradlew test` suite because it
typechecks the entire arend-lib, which takes several minutes.

The `-Darend.roundtrip.modules` system property accepts a comma-separated list
of module paths (e.g. `Algebra.Ring`, `Topology.CoverSpace.Complete`).  Module
paths use dots as separators and correspond to the directory/file structure under
`arend-lib/src/` (e.g. `Algebra.Ring` → `arend-lib/src/Algebra/Ring.ard`).

The test writes a detailed log to a temporary file.  The path is printed at the
start and end of the test output.  Grep the log for `ERROR:` to see all
failures.

### Interpreting errors

Errors fall into several categories, identifiable by their log prefix:

| Log prefix | Meaning |
|---|---|
| `CORE_CHECK_SKIPPED` | Core checker error that also occurs on the original (not a serialization issue — core checker limitation) |
| `CORE_CHECK_WARN` | Core checker error only in the deserialized version (typically caused by expression sharing loss, not data corruption) |
| `CORE_CHECK_ERROR` | Genuine core checker error introduced by serialization |
| `DISCREPANCY_CONTENT` | Pretty-printed definitions differ between original and loaded |
| `SERIALIZE_FAIL` | `persist()` returned false |
| `DESERIALIZE_NULL` | `load()` returned null |
| `DESERIALIZE_ERROR` | `load()` reported errors |

The test baselines core check results against the **original** (non-serialized)
definitions.  Errors that also occur on the original are `SKIPPED` — these are
core checker limitations (e.g. `DummyEquations` cannot solve level constraints),
not serialization bugs.  Errors only in the deserialized version are logged as
`WARN` (typically caused by expression sharing loss — see below) rather than
counted as test failures; the pretty-print comparison is the authoritative
structural check.

Core checker error messages and what they indicate:

| Message pattern | Likely cause |
|---|---|
| `Variable 'X' is not bound` | Binding identity mismatch: a `ReferenceExpression` points to a different object than the one added to the checker's scope.  Usually caused by `existing_ref` pointing to stale bindings or bindings being re-registered with new indices. |
| `Type mismatch` | Structural type difference.  A parameter or result type in the loaded definition doesn't match the expected type.  Can be caused by incorrect substitution (due to binding identity issues) or by wrong type serialization.  Also frequently a false positive from expression sharing loss (see below). |
| `Binding 'X' is already bound` | The same DependentLink object appears in two nested scopes (e.g. a Pi parameter reused inside a Lambda body via `existing_ref` when it shouldn't be). |
| `Cannot convert pattern` | Pattern deserialization produced a pattern that doesn't match the expected constructor or type. |

**Expression sharing loss:** The original expression tree may share Java objects
(DAG structure) — e.g. the same `FunCallExpression` appears in both the result
type and the body.  `CompareVisitor` short-circuits via `expr1 == expr2` for
shared objects.  After deserialization, separate objects are created for each
protobuf occurrence.  The structural comparison can then fail when one side has
a `ClassCallBinding` reference (`this`) while the other has the expanded form,
because `ClassCallBinding` is not an `EvaluatingBinding` and does not reduce.
These mismatches are semantically harmless — the definitions are correct.

## Debugging workflow

### 1. Narrow the scope

Start with a single module that has errors:

```bash
./gradlew test ... -Darend.roundtrip.modules="Algebra.Ring"
```

The test reports the exact definition name (e.g. `Algebra.Ring :: CRing.bezout_finitelyGenerated_principal`).

### 2. Create a minimal reproducer

Create a synthetic `.ard` file under `arend-lib/src/` (e.g. `Debug/SerTest.ard`)
with a small definition that triggers the same error pattern.  Run the test on
that module:

```bash
./gradlew test ... -Darend.roundtrip.modules="Debug.SerTest"
```

Iteratively simplify the definition until you have the smallest code that
reproduces the error.  Delete the file when done.

### 3. Add diagnostic info (temporarily)

To identify binding identity issues, temporarily modify
`CoreExpressionChecker.visitReference` (the "not bound" check at ~line 292) to
include the binding's class name, identity hash, and the current context
contents:

```java
if (myContext != null && !myContext.containsKey(expr.getBinding())) {
  String debugInfo = "Variable '" + expr.getBinding().getName() + "' is not bound"
      + " [bindingClass=" + expr.getBinding().getClass().getSimpleName()
      + ", id=@" + Integer.toHexString(System.identityHashCode(expr.getBinding()))
      + ", contextBindingNames=" + myContext.keySet().stream()
          .map(b -> b.getName() + "@" + Integer.toHexString(System.identityHashCode(b))
                    + ":" + b.getClass().getSimpleName())
          .collect(java.util.stream.Collectors.joining(", "))
      + "]";
  throw new CoreException(CoreErrorWrapper.make(
      new TypecheckingError(debugInfo, mySourceNode), expr));
}
```

This tells you whether there is a same-named binding in context with a different
identity (binding identity bug) or whether the binding is entirely absent
(missing registration).

### 4. Trace serialization ↔ deserialization

The two key files are:

- **Serialization:** `base/src/main/java/org/arend/module/serialization/ExpressionSerialization.java`
- **Deserialization:** `base/src/main/java/org/arend/module/serialization/ExpressionDeserialization.java`

Bindings are tracked via:
- Serialization: `myBindingsMap : Map<Binding, Integer>` — maps binding objects to
  sequential indices.  `registerBinding(binding)` assigns an index.
  `writeBindingRef(binding)` looks up the index.
- Deserialization: `myBindings : List<Binding>` — indexed list of binding objects.
  `registerBinding(binding)` appends to the list.  `readBindingRef(index)` returns
  the binding at that position.

The `existing_ref` mechanism allows reusing a previously registered binding
instead of creating a new one.  This is critical for preserving object identity
when the same DependentLink chain appears in multiple places (e.g. Pi/Lambda
parameter sharing, Sigma type parameters shared between result type and tuple
body).

### 5. Understand the serialization order

For a **function definition** (`DefinitionSerialization.writeFunctionDefinition`):
1. Parameters (`writeParameters`)
2. Result type (`writeExpr`)
3. Body (`writeBody` → `writeElimBody` or `writeExpr`)

For a **CaseExpression** (`ExpressionSerialization.visitCase`):
1. Elim body (patterns + clause bodies)
2. Case parameters
3. Result type
4. Arguments

The order matters because bindings registered during earlier steps become
available as `existing_ref` targets in later steps.  Re-registration (writing a
binding that's already in `myBindingsMap`) overwrites the index, which can cause
later `existing_ref` values to resolve to wrong bindings during deserialization.

## Key invariant

**Binding identity must be preserved across the round-trip.**

If two places in the original expression tree reference the same `Binding`
object, the deserialized tree must also reference the same object.  Conversely,
if two places reference different objects, they must remain different.

Violations of this invariant manifest as:
- "Variable X is not bound" — reference and scope use different objects
- "Binding X is already bound" — two scopes share one object when they shouldn't
- Type mismatch — substitution fails because it maps one binding object but the
  type references a different one with the same name

## Common bug patterns

### 1. DependentLink chain truncation

**Symptom:** `IllegalStateException` at `EmptyDependentLink.getType`.

**Cause:** `readParameters` doesn't link an `existing_ref` telescope after a
normal telescope.  The chain ends at the last normal binding; the existing
binding is disconnected.

**Fix location:** `ExpressionDeserialization.readParameters` — when an
existing_ref follows a normal telescope and the chain currently ends
(`last.getNext()` is `EmptyDependentLink`), explicitly link via
`last.setNext(existingLink)`.

### 2. Multi-variable telescope re-registration

**Symptom:** "Variable X is not bound" where X is an `UntypedDependentLink`
(typically `i`, `j`, `a`, `b` — the first variable in a multi-variable telescope
like `(i j : Nat)`).

**Cause:** `writeSingleParameter` doesn't write `existing_ref` for
multi-variable telescopes even when all links are already registered.  It
re-registers them with new indices.  A subsequent single-variable telescope
(e.g. `(_ : i = i)`) correctly gets `existing_ref` pointing to the OLD index.
On deserialization, the single-variable telescope resolves to the old binding
whose type references the old `i`, while the multi-variable telescope creates a
fresh `i`.  The core checker finds the old `i` unreachable.

**Fix location:** `ExpressionSerialization.writeSingleParameter` — extend the
`existing_ref` condition to allow multi-variable telescopes when ALL links from
`link` to `typed` are already in `myBindingsMap`.

### 3. HaveClause / LetClause identity

**Symptom:** "Variable X is not bound" where X is a `HaveClause` or
`LetClause`.

**Cause:** The same `LetExpression` object appears multiple times in the
expression tree (DAG/sharing).  Each time `visitLet` encounters it, it
re-registers the `HaveClause` bindings with new indices, overwriting the old
ones.  References serialized before the overwrite use the old index.  During
deserialization, each visit creates separate `HaveClause` objects, so the
reference resolves to a different object than the one in the LetExpression's
clause list.

**Fix:** Added `existing_ref` field to `Let.Clause` protobuf.  In
`visitLet`, if a clause is already registered, emit `existing_ref` instead
of re-serializing.  In `readLet`, if `existing_ref > 0`, look up the
existing `HaveClause` binding instead of creating a new one.

**Fix location:** `ExpressionSerialization.visitLet`,
`ExpressionDeserialization.readLet`, `ExpressionProtos.proto` (Let.Clause).

**Status:** Fixed as of 2026-04-11.  Previously affected ~3 definitions
(`Confluence::unify-top`, `Locale::surj_regular`, `Monoid::FinSum-inj`).

### 4. ClassCallBinding / `this` renaming

**Symptom:** Content mismatch where the pretty-printed definitions differ only
in `this` numbering (e.g. `this3` vs `this8`, `this20` vs `this15`).

**Cause:** Every `ClassCallExpression` has its own `ClassCallBinding` inner
object, always named `this`.  When multiple `ClassCallBinding`s are in scope,
the pretty-printer (`ToAbstractVisitor`) disambiguates by appending a numeric
suffix (`this`, `this2`, `this3`, …).  The suffix is assigned by a renamer
that tracks bindings by Java object identity — each distinct `ClassCallBinding`
gets the next available number.

The suffix therefore depends on the **order** in which bindings are encountered
during pretty-printing, which is determined by binding indices (their position
in the serializer's binding map).  After deserialization the indices can differ
from the original because:

- A `ClassCallExpression` reused via `existing_this_binding_ref` retains its
  original index, but any *new* `ClassCallExpression` (not shared) gets a fresh
  `ClassCallBinding` registered at whatever index the deserializer is at.
- Serialization visits result types before bodies.  If these contain
  `ClassCallExpression`s in a different order than the original typechecker
  created them, the indices shift.
- Field implementations inside a `ClassCallExpression` can contain nested
  `ClassCallExpression`s whose registration order depends on the serialization
  traversal, which may differ from the typechecking order.

The values referenced by these `this` bindings are correct — they point to the
right `ClassCallExpression` with the right implementations.  Only the numbering
differs.

**Status:** Cosmetic issue, does not affect correctness.  ~18 occurrences.

### 5. FieldCallExpression unfolding on deserialization

**Symptom:** Content mismatch where the pretty-printed definitions differ in
how a field call is qualified — e.g. `contains_+ {\new S1 {}}` (original) vs
`S.contains_+` (loaded), or `contains_+ {\new integralClosure-subring …}`
vs bare `contains_+`.

**Cause:** `FieldCallExpression.make()` unfolds `NewExpression` arguments: if
the field-call receiver is a `NewExpression`, `make()` returns the field
implementation directly instead of a `FieldCallExpression`.  The original
expression tree can contain a `FieldCallExpression` whose argument IS a
`NewExpression` (constructed by the typechecker without going through `make`).
When this expression is serialized as a `FieldCall` protobuf and then
deserialized, the deserializer called `make()`, which unfolded the
`NewExpression` argument and discarded the `FieldCallExpression` wrapper.

**Fix:** Added `FieldCallExpression.makeExact()` — a factory method that
creates a `FieldCallExpression` without unfolding.  Changed
`ExpressionDeserialization.readFieldCall` to use `makeExact` instead of `make`.

**Fix location:** `FieldCallExpression.makeExact`,
`ExpressionDeserialization.readFieldCall`.

**Status:** Fixed as of 2026-04-11.  Previously affected ~5 definitions
(`SubPseudoSemiring.cStruct`, `SubSemiring.cStruct`, `SubPseudoRing.cStruct`,
`SubRing.cStruct`, `integralClosure`).

## File reference

| File | Role |
|---|---|
| `src/test/java/org/arend/library/ArendLibRoundTripTest.java` | Round-trip test |
| `base/.../serialization/ExpressionSerialization.java` | Expression → protobuf |
| `base/.../serialization/ExpressionDeserialization.java` | Protobuf → expression |
| `base/.../serialization/DefinitionSerialization.java` | Definition → protobuf |
| `base/.../serialization/DefinitionDeserialization.java` | Protobuf → definition |
| `base/.../expr/FieldCallExpression.java` | Field-call expression; `makeExact` preserves structure on deserialization |
| `base/.../doubleChecker/CoreExpressionChecker.java` | Core type-checker (used for validation) |
| `base/.../doubleChecker/CoreDefinitionChecker.java` | Definition-level checker; uses `LenientEquations` for level comparisons |
| `base/.../doubleChecker/CoreModuleChecker.java` | Module-level checker |
| `base/.../context/param/DependentLink.java` | DependentLink chain interface |
| `base/.../context/param/TypedDependentLink.java` | Typed link (`hasNext()` always true) |
| `base/.../context/param/UntypedDependentLink.java` | Untyped link in multi-var telescope |
| `base/.../context/param/EmptyDependentLink.java` | Chain terminator (`hasNext()` false, `getType()` throws) |
| `base/.../context/LinkList.java` | Mutable linked-list builder for DependentLink chains |
| `proto/src/main/proto/ExpressionProtos.proto` | Protobuf schema for expressions |
| `build.gradle.kts` | Forwards `arend.roundtrip.modules` to the test JVM |
