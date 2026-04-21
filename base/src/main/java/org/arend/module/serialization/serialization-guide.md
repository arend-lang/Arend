# Arend ARC Serialization — Maintenance Guide

Notes accumulated while fixing the partial round-trip test (`ArendLibPartialRoundTripTest`), which exercises the path *fresh typechecking against deserialized prerequisites*. If you're chasing a serialization bug, read this before instrumenting from scratch — most questions have come up before.

## The one invariant you must remember

**For a deserialized `ConcreteGroup`, `group.definition()` is always `null`.**

`ModuleDeserialization.readGroup` reconstructs only the `Definition` (core) shells; it never rebuilds the `Concrete.ResolvableDefinition`. Every piece of machinery that assumes `definition instanceof Concrete.ClassDefinition`/`DataDefinition`/`FunctionDefinition` silently returns empty for deserialized groups. This is the single most common source of "looks fine in source, broken after serialize/load" bugs in this subsystem.

Any code that reads shape off `group.definition()` needs a fallback that reads the same shape off `group.referable().getTypechecked()` (the core `Definition`).

Known fallbacks already added (as of 2026-04-21):
- `ConcreteGroup.getFields()` / `getConstructors()` / `getInternalReferables()` — walks `ClassDefinition.getPersonalFields()` / `DataDefinition.getConstructors()`.
- `TypingInfoVisitor.processGroup` — registers `DynamicScopeProvider` *and* `AbstractBody` (via `registerCoreType`) for deserialized classes and functions.
- `InstanceCacheImpl.addInstances` — detects `\instance` via `fnDef.getKind() == INSTANCE`.
- `CollectDefCallsVisitor.initializeInstances` / `fillInstanceMap` — extracts class refs from `fnDef.getResultType()` (ClassCall) and super-chains from `ClassDefinition.getSuperClasses()`.

If you write new machinery that touches `ConcreteGroup`, grep the call sites: any branch gated on `instanceof Concrete.ClassDefinition`/`DataDefinition`/`FunctionDefinition` is a potential hole.

## The load-order trap

`bin.load()` calls `readGroup` (creates Definition shells) + `readModule` (fills in types via `fillInCallTargetTree`). `fillInCallTargetTree` resolves cross-module references like `Set:BaseSet.E` by calling `ref.getTypechecked()` on a stored `FieldReferable`. **If the dependency module hasn't finished its own `readGroup` yet, `getTypechecked()` returns null and `readModule` throws `"Definition X:Y is not loaded"` — aborting `readModule` mid-flight while leaving the group partially registered in the server.** Downstream code sees a subgroup with null class-field types.

**Fix pattern**: always load ARCs in topological order of imports, using the source-side server's raw groups to derive the graph. See `ArendLibPartialRoundTripTest.topoSortByImports`. Don't rely on iteration order of `server.getModules()` (it's BFS from the target, not dep order).

## Two kinds of "the typechecker sees nothing":

### A. Empty scope for deserialized classes

Resolving `instanceRef.fieldName` goes through `LongUnresolvedReference.resolve`, which calls `typingInfo.getTypeDynamicScopeProvider(instanceRef)` → internally `getRefType(instanceRef)` → returns the target class's provider. If `getRefType` was never populated for the deserialized def, the resolver falls through to the `Concrete.FieldCallExpression` fallback at `LongUnresolvedReference.java:288`, and `CheckTypeVisitor.visitFieldCall:2063`'s `findField` — which only searches personal **fields**, not lemmas/static methods — returns null and reports `"Cannot find 'X' in class 'Y'"`.

Fix: `TypingInfoVisitor.processGroup`'s deserialized branches must call both `addDynamicScopeProvider` **and** `addReferableType`.

### B. Empty instance list at typechecker startup

`GlobalInstancePool.myInstances` comes from `TypecheckingOrderingListener.getInstances(def)` → reads `myInstanceDependencies.get(def)` → populated by `Ordering.forDependencies` only when the intersection of `myInstanceScopeProvider.getInstancesFor(def).getInstances()` and the `CollectDefCallsVisitor`'s `instanceDependencies` is non-empty.

`CollectDefCallsVisitor.initializeInstances` (line 146) iterates `myInstances.getInstances()` and, for each instance, pulls `myConcreteProvider.getConcrete(instance)` to find its target class from the concrete result type. **For deserialized instances `getConcrete` returns null**, so they're silently skipped → `myInstanceMap` stays empty for their classes → `myInstanceDependencies` never gets populated → `GlobalInstancePool` starts with an empty instance list → "Cannot infer an instance of class X" on every search.

If you see `findInstance failed ... total=0` (the `-Darend.instance.debugMatchClass=X` diagnostic), the instance pool is empty — the bug isn't in matching but upstream in list population.

## Access modifiers don't round-trip without proto support

Before the fix documented below, the `Referable` proto had no `access_modifier` field. Deserialization hardcoded `AccessModifier.PUBLIC` for every subgroup and field. `LexicalScope.find:78` filters DYNAMIC walks to PUBLIC only — so source-mode `\protected \func op` was hidden, but post-deserialization every `op` (Preorder.op, Semigroup.op, Group.op, …) became PUBLIC and leaked into scope. Any pair of imports each exposing an `op` then fired `DuplicateOpenedNameError`. Fixed in `Definition.proto` + `writeReferable` (both `DefinitionSerialization` and `ModuleSerialization`) + `readAccessModifier` in `ModuleDeserialization`. Default `PUBLIC_ACCESS=0` keeps old `.arc` files backward-compatible.

**Moral**: if a flag lives on `GlobalReferable` and affects scope visibility / matching behavior, make sure it's in the proto.

## ClassDefinition identity across modules — it actually works

Don't waste time on this theory. When module M1 defines class C and module M2 references it, `SimpleCallTargetProvider.putCallTarget` uses `putIfAbsent` + `getTypechecked()` and correctly returns the **same Java object** across modules. Probed this explicitly in the session — `Preorder via Order.PartialOrder` and `Preorder via Algebra.Ordered.PosetAddMonoid.getSuperClasses()` had identical `identityHashCode`. If you see an "Cannot-infer-instance" or "isSubClassOf returns false" bug, it's much more likely to be about *how* the instance list was populated (see §B above), not about class object identity.

Two subtleties to keep in mind though:
- The `FieldReferable` / static-subgroup referable objects are created per-deserialization-pass; cross-module usage works because all lookups go through the shared `putIfAbsent` map.
- `myConcreteProvider` is a *different* identity space. It's populated from `defMap` which `ArendCheckerImpl.resolveModules` fills only from `group.definition()` — i.e., never populated for deserialized groups. That's why `getConcrete(deserializedRef)` returns null even when `getTypechecked(deserializedRef)` is valid.

## Diagnostic infrastructure to reuse

All guarded behind system properties; zero cost when unset.

| Flag | Effect | Where |
|---|---|---|
| `-Darend.subst.maxDepth=N` | `SubstVisitor.visitPi` throws `SubstDepthExceeded` at depth N | `base/.../core/subst/SubstVisitor.java` |
| `-Darend.instance.maxDepth=N` | `GlobalInstancePool.findInstance` throws `InstanceDepthExceeded` at depth N | `base/.../typechecking/instance/pool/GlobalInstancePool.java` |
| `-Darend.instance.debugMatchClass=ClassName` | `GlobalInstancePool.getInstancePair` logs per-sub-check failure counts when searching for instances of `ClassName` | same file |

The test itself (`ArendLibPartialRoundTripTest`) has Phase 2.5 validation: loads the cone in topo order into a fresh server, walks every typechecked Definition, calls `getTypeWithParams` / `getType`, records any SO/NPE/AssertionError. If this phase is clean but Phase 3 isn't, the problem is in the *interaction* between fresh typechecking and deserialized prerequisites — not in the deserialized definitions themselves.

## Methodology that worked

When a specific category of errors appears in Phase 3 but not Phase 0 (the from-source baseline):

1. **Sample one** error, find the exact source expression, trace through which checker path produces the error (e.g. `CheckTypeVisitor.visitFieldCall:2063`).
2. **Find the precondition**. Work backwards from the error site to what the resolver/orderer had to supply. Usually it's `typingInfo.getRefType(ref)` or `myConcreteProvider.getConcrete(ref)` or `ScopeContext.DYNAMIC getElements` — one of the "shape" queries.
3. **Probe whether that precondition is satisfied** — add a throwaway probe in the test that directly asks the same question on server2 after Phase 3 resolution has run. Check identity and contents.
4. **If it returns null or empty**, walk backward to find the populator. For per-module state it's `ArendCheckerImpl.resolveModules` → `TypingInfoVisitor.processGroup` or `DefinitionResolveNameVisitor.resolveGroup` or `InstanceCacheImpl.addInstances` or similar.
5. **Check whether that populator has a branch that skips deserialized groups**. Usually yes, gated on `group.definition() instanceof Concrete.X`. Add a fallback that reads the same info from the typechecked `Definition`.

Every serialization fix in this work series followed that pattern.

**What does not work**: speculating about class-object aliasing, substitution cycles, deserialization of shared references. Those bugs exist too but are not the dominant pattern — nine out of ten times the answer is "some code branch silently returns empty for deserialized groups".

## Checklist when adding a new field/flag to the proto

1. Add the field to `proto/src/main/proto/*.proto`.
2. Update the *serializer* that writes the owning proto (usually in `base/.../module/serialization/DefinitionSerialization.java` or `ModuleSerialization.java`).
3. Update the *deserializer* in `ModuleDeserialization.java` — note `readGroup` and `readDefinitions` take different paths, both may need updating.
4. Default proto value (0 or `""` or empty) must produce behavior compatible with old `.arc` files.
5. If the flag affects **scope** (visibility, precedence, etc.), run `ArendLibPartialRoundTripTest` — scope bugs show up there much earlier than in `ArendLibRoundTripTest` (which only tests self-consistency, not interaction with fresh typechecking).

## Known remaining error classes (as of 2026-04-21)

After six sessions of fixes, Phase 3 of `ArendLibPartialRoundTripTest` (target `AG.Projective`) has **350 secondary errors** down from 3754 original:

- ~15 `Type mismatch` — some `Precat.Ob` / Category-theory coercion issues, structurally different expected/actual types. Likely ClassCallBinding-identity territory.
- ~60 `Cannot infer parameter 'x'/'y' of definition '+-rat'/'*-rat'/...` — concentrated in Arith.Real.* modules. Likely cascade from some deeper typechecking issue in a specific definition.
- ~13 `Cannot infer an instance of class 'Preorder'` (residual after the `CollectDefCallsVisitor` fix).
- ~11 `Cannot solve the equation` — concentrate in a few modules.
- ~9 `Expression is applied to an argument, but does not have a function type`.
- Long tail.

Each is a candidate for the step-4/5 methodology above.

## Useful file locations (quick reference)

- Serialization: `base/src/main/java/org/arend/module/serialization/`
  - `ModuleSerialization.java`, `ModuleDeserialization.java` — module-level writeGroup/readGroup.
  - `DefinitionSerialization.java`, `DefinitionDeserialization.java` — per-definition.
  - `ExpressionSerialization.java`, `ExpressionDeserialization.java` — per-expression.
  - `SimpleCallTargetProvider.java` / `SimpleCallTargetIndexProvider.java` — the `putIfAbsent`-based cross-module identity.
- Proto schemas: `proto/src/main/proto/`
- Scope: `base/src/main/java/org/arend/naming/scope/LexicalScope.java` (entry point for group-based lookups)
- Typing info: `base/src/main/java/org/arend/naming/resolving/typing/TypingInfoVisitor.java` — where deserialized-fallbacks cluster.
- Instance search: `base/src/main/java/org/arend/typechecking/instance/pool/GlobalInstancePool.java`
- Ordering: `base/src/main/java/org/arend/typechecking/order/Ordering.java` (populates `myInstanceDependencies`).
- The test: `src/test/java/org/arend/library/ArendLibPartialRoundTripTest.java`.
- Existing full-round-trip test: `src/test/java/org/arend/library/ArendLibRoundTripTest.java` — self-consistency only; doesn't exercise the fresh-vs-deserialized interaction.
