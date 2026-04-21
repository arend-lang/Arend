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
- `CollectDefCallsVisitor.visitReference` / `addCoreParameterClassReferences` — when a reference points to a deserialized definition (concrete unavailable), scans the core `DependentLink` parameters for `ClassCallExpression`-typed params and calls `addInstances` for each. Without this, `Ordering.forDependencies` never discovers which instances are needed for the definition, leaving `GlobalInstancePool.myInstances` empty (`total=0`). Same fallback added for the per-instance parameter scan in `addInstances` (the method that registers transitive class dependencies from instance parameters).

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

After seven sessions of fixes, Phase 3 of `ArendLibPartialRoundTripTest` (target `AG.Projective`) has **291 secondary errors** down from 3754 original (350 before the `CollectDefCallsVisitor.addCoreParameterClassReferences` fix). These errors have been categorized into six root-cause groups below (A–F). The numbers reflect what the current test run produces.

### Category A: Instance-inference failures (17 errors, down from 37)

**Symptom**: `Cannot infer an instance of class 'X'`.

**Breakdown by class** (after `addCoreParameterClassReferences` fix):
- `Preorder`: 5 errors (down from 13) — remaining in Arith.Real.UpperReal, Analysis.Series, Topology.MetricSpace.UpperReal, Topology.NormedAbGroup.Real.Functions, Topology.MetricSpace
- `Precat`: 4 errors (Algebra.Group.Representation.{Irreducible,Category}, Category.{KanExtension,Topos.Presheaf})
- Long tail of 1-off classes: `CMonoid`, `Monoid`, `Cone`, `Limit`, `ExPseudoMetricSpace`, `ExPseudoNormedAbGroup`, `Functor`, `SmithDomain` (8 errors total)

**Likely root causes**: Two distinct mechanisms at play:
1. **Partially fixed `CollectDefCallsVisitor` gap**: The `addCoreParameterClassReferences` fix (see fallbacks list above) reduced `Preorder` errors from 13 to 5 by teaching `visitReference` to scan core `DependentLink` parameters when concrete is unavailable. The remaining 5 `Preorder` failures likely involve definitions where the `Preorder` dependency comes through a path not covered by parameter scanning (e.g., result-type coercions, inherited field types, or intermediate concrete definitions that reference deserialized ones whose parameters in turn reference `Preorder`). Probe output shows `InstanceCache match-fail log: 262 failures` — most are `failCompare` (classifying expression doesn't match) rather than `total=0` (empty pool). The `total=0` cases in the debug log are class definitions like `BooleanAlgebra` that intentionally skip super-class instances — these don't cause errors.
2. **`Precat` cluster**: These 4 errors are in category theory modules that depend on deserialized category-theory infrastructure. The `Meta 'SmallPrecat' is empty` errors (Category E below) in the same modules suggest the meta extension can't find the `Precat` instance it needs, implying the meta's own instance lookup path has the same gap.

**Suggested investigation**: The remaining `failCompare=3` / `failCompare=6` cases need deeper investigation. The classifying expression comparison (`GlobalInstancePool.compareClassifying`) might fail because the deserialized classifying expression normalizes differently or because `FieldCallExpression` doesn't match across deserialization boundaries.

### Category B: "Cannot find X in class Y" — missing dynamic-scope members (8 errors)

**Symptom**: `Cannot find 'IRing' in class 'SubRing'`, `Cannot find 'func-BigSum' in class 'LinearMap'`, `Cannot find 'func-minus' in class 'LinearMap'`, `Cannot find 'func-BigProd' in class 'RingHom'`, `Cannot find 'ICRing' in class 'CSubRing'`, `Cannot find 'f_ret' in class 'Equiv'`.

**Affected modules**: Algebra.Ring.QuotientProperties, Algebra.Field.AlgebraicClosure, Algebra.Ring.Integral.MinPoly, Algebra.Linear.VectorSpace, Analysis.Derivative, Analysis.StrongDerivative, Algebra.Field.Algebraic.

**Likely root cause**: These are all inherited class members accessed via dot-syntax (`myRingHom.func-BigSum`). The resolver goes through `LongUnresolvedReference.resolve` → `typingInfo.getTypeDynamicScopeProvider(ref)` → walks the class's dynamic scope. If the deserialized class's `DynamicScopeProvider` doesn't expose inherited-from-superclass members (only personal fields), these lookups fail. This is the same mechanism as §A ("Empty scope for deserialized classes") in the guide above. The Phase 2.5 DOF probe confirms: `no DynamicScopeProvider registered!` in the standalone validation server, though Phase 3 shows the provider IS registered (size=15). The missing members (`func-BigSum`, `func-minus`, `IRing`, `ICRing`, `f_ret`) are defined in **ancestor** classes, not the class being queried — so the provider must walk the super-class chain and collect inherited dynamic content. Check whether `TypingInfoVisitor.processGroup`'s deserialized branch builds the `DynamicScopeProvider` with full super-class content or only personal fields.

### Category C: Cascading parameter-inference failures in Arith.Real.* (~168 errors)

**Symptom**: `Cannot infer parameter 'x'/'y' of definition '+-rat'/'*-rat'/'+_U'/'+_L'/'rat_<=-dec'/...` and generic `Cannot infer parameter 'P'/'x'/'y'` without a named definition.

**Top definitions by frequency**: `+-rat` (30), `rat_<=-dec` (9), `+_U` (9), `*-rat` (9), `+_L` (6), `<->trans` (5), `pow>=0` (4), `+` (4), `minus-rat` (4), `*_U` (3), `abs>=0` (3).

**Top affected modules**: Arith.Real.UpperReal (39 errors), Arith.Real (22), Topology.BanachAlgebra (21), Arith.Real.Field (17), Arith.Real.InfReal (16), Analysis.Series (15), Topology.MetricSpace.Nat (14), Topology.StoneCStarAlgebra (12).

**Likely root cause**: These are *cascade errors*. The definitions `+-rat`, `*-rat`, `+_U`, `+_L`, etc. are arithmetic operations on reals defined in the deserialized cone. When the typechecker tries to use them in fresh modules, implicit parameters can't be inferred because either:
1. An instance that would constrain the implicit parameter is missing (back to Category A), or
2. A type mismatch earlier in the expression prevents unification from propagating constraints to implicit parameters.

These are NOT 194 independent bugs — they are the *downstream blast radius* of a small number of root causes (likely 2–4 bugs total). Fixing the instance-inference issues (Category A) and the class-scope issues (Category B) will likely eliminate most of these.

**Key evidence**: The errors cluster heavily around `Arith.Real.*` and `Topology.*` modules that use the `DiscreteOrderedField` / `LinearlyOrderedSemiring` class hierarchy. The `InstanceCache match-fail log: 291 failures` with debug class `Preorder` confirms instance resolution is failing broadly, which cascades to parameter inference.

### Category D: Type mismatches and structural errors (15 + 9 + 9 + 4 = 37 errors)

**Symptoms**:
- `Type mismatch` (15 errors): TM_DUMP shows patterns like `expected: \Type, actual: Rat`; `expected: a data type, actual: U {\this} q`; and one case where `same-printed=true` (expected and actual print identically but aren't equal — likely ClassCallBinding-identity issue).
- `Expression is applied to an argument, but does not have a function type` (9 errors): A definition that should resolve to a function type (e.g., a class field returning a Pi-type) isn't being recognized as one.
- `Cannot find subexpression` (9 errors): Typically downstream of a type mismatch — the `simplify` or `rewrite` meta can't locate the subexpression it wants to rewrite because the types don't match.
- `The type of a lemma must be a proposition` (4 errors): The result type isn't recognized as `\Prop` due to upstream inference failure.

**Affected modules**: Spread across Topology.Compact, Arith.Real.*, Category.Yoneda, Category.KanExtension, Algebra.Group.QuotientProperties, Algebra.Linear.VectorSpace, Analysis.Series, Arith.Nat.EulerTotient.

**Likely root causes**: Mix of:
1. **Cascade from categories A/C**: When implicit parameters can't be inferred, the resulting `{?error}` holes produce type mismatches downstream.
2. **Genuine structural divergence**: The `same-printed=true` TM_DUMP case suggests that the deserialized type and the freshly-computed type print the same but compare as different Java objects — possibly because `ClassCallExpression.Binding` objects aren't shared correctly across the deserialization boundary. This is rare (only ~1 case) but would be a real serialization bug.
3. **"Not a function type"** errors (9): Likely same cascade — a definition's type can't be inferred, so it appears as `{?error}` instead of a Pi-type.

### Category E: Meta-extension failures (12 errors)

**Symptom**: `Meta 'SmallPrecat' is empty` (3), `Meta 'cycle' is empty` (6), `Meta 'proof' is empty` (2), `Meta 'Colimit' is empty` (1).

**Affected modules**: Category.{Yoneda, Topos.Presheaf, KanExtension}, Algebra.{Ring.ZeroDimensional, Ring.Integral.MinPoly, Field.AlgebraicClosure}, Topology.{CoverSpace, CoverSpace.Complete}, Analysis.Limit.

**Likely root cause**: Meta-extensions (`\meta cycle`, `\meta proof`, `\meta SmallPrecat`, `\meta Colimit`) run Arend typechecker code internally. When they can't find the definitions or instances they need (because of Category A instance-pool gaps), they produce an empty result and report "Meta X is empty". These are downstream of the same root causes.

### Category F: Rare/unique errors (16 errors)

- `Expected a class or a class instance` / `Expected a class` (2, Category.Yoneda): An expression that should resolve to a class doesn't — likely because the deserialized Precat/Cat class wasn't recognized.
- `The following fields are not implemented: C, D, F, G` / `...J, D, G, isLimit` (2, Category.Yoneda): Downstream of failing to recognize an expression as a class instance.
- `Cannot apply extensionality` (4): The extensionality meta can't find the ext-function — likely instance issue.
- `Nothing to simplify` (3): The `simplify` meta finds nothing to work with.
- `Clause is redundant` / `Conditions check failed` (2, Algebra.Ring.QuotientProperties): Pattern-matching errors, likely downstream of the "Cannot find IRing in class SubRing" issue in that same module.
- `Termination check failed` (1, Topology.BanachAlgebra): Likely cascade — if types are wrong, the termination checker sees a different recursion pattern.
- `Meta 'rewrite' failed` (1), `Expressions are not equal` (1), `Function was not unfolded` (1), `Unrecognized type` (1), `Cannot infer a contradiction` (1), `The type of the equation should be LinearlyOrderedSemiring` (1), `The expected type should be either an equation or the empty type` (1), `The left path endpoint mismatch` (1): All one-off cascade errors in various modules.

---

### Priority ordering for fixes

The 291 errors likely reduce to **3–4 independent root causes**:

1. **Instance pool population — partially fixed (Categories A + C + E, ~200 errors remaining)**: The `addCoreParameterClassReferences` fix reduced instance failures from 37→17 and total errors from 350→291 by teaching `CollectDefCallsVisitor.visitReference` to scan core parameters when concrete is unavailable. The remaining 17 instance failures split into: (a) `failCompare` cases where the classifying expression comparison fails across the deserialization boundary (likely `FieldCallExpression` normalization difference), and (b) `Precat`/category-theory cases where the meta extension's instance lookup has the same gap.

2. **Dynamic scope for inherited class members (Category B, 8 errors + cascades)**: `DynamicScopeProvider` for deserialized classes doesn't expose inherited members. Fix in `TypingInfoVisitor.processGroup`.

3. **ClassCallBinding identity (Category D, ~5 genuine errors)**: The `same-printed=true` type mismatch suggests a binding-identity issue across deserialization boundary. Low count but worth investigating separately from cascade noise.

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
