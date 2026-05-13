# Gap 1: `getClassParameters` truncates at deserialized super-classes

## Where

`base/src/main/java/org/arend/typechecking/visitor/CollectDefCallsVisitor.java`,
inside `getClassParameters` (lines 293-316):

```java
for (Concrete.ReferenceExpression refExpr : last.getSuperClasses()) {
  if (refExpr.getReferent() instanceof TCDefReferable superClassRef
      && myConcreteProvider.getConcrete(superClassRef) instanceof Concrete.ClassDefinition superClass) {
    toVisit.add(superClass);
  }
  // ← else branch silently drops the super
}
```

When `myConcreteProvider.getConcrete(superClassRef)` returns `null` (i.e. the
super-class is deserialized from ARC, not loaded as source), the loop body
short-circuits and the walk truncates at that boundary.

Two sibling sites in the same file already have the deserialized fallback:

- `fillInstanceMap` line 146-152 — walks `deserClass.getSuperClasses()` from
  the core `ClassDefinition` when concrete is missing.
- `addCoreParameterClassReferences` line 417-442 — class branch walks
  `classDef.getSuperClasses()` + `classDef.getAllFields()` for fully
  deserialized class refs.

So the visitor *generally* knows how to deal with deserialized class graphs.
`getClassParameters` is the lone unpatched site.

## What the gap actually misses

`getClassParameters` collects **parameter-fields** — the fields that come
from a class header like `\class Map (C : Precat) { … }`. The walk visits
each class in the chain and appends any `field.getData().isParameterField()`
field's type as a synthetic `Concrete.TypeParameter`. The result is fed into
`addParametersClassReferences`, which calls `addInstances(...)` for any
class-typed parameter type.

So when the truncation fires, what's missed is:

> Parameter-fields of a deserialized super-class whose types are classes.

For most source-side hierarchies this is empty:
- Body fields (operations like `<`, `*`) don't count.
- Parameter-fields without class-typed declarations don't count.

The narrow case that *would* be affected: `\class Iso \extends Equiv { … }`
where `Equiv` was deserialized and carries a header parameter
`\class Equiv (C : Precat)`. With Gap 1, walking from `Iso` would not pick
up the demand for `Precat` instances. Commit `6f8eda252` already plugged
exactly this case in `addCoreParameterClassReferences:430-441`, but only
when the *outer* class is also deserialized.

## Why the contradiction repro didn't surface it

The contradiction repro that brought us here had a different root cause —
`SerializableKeyRegistry` plumbing — and its trigger path never used
`getClassParameters` in a meaningful way. Probes confirmed:

- `dec<_reduce` runs `addInstances(Dec, …)` via `addParametersClassReferences`
  on its own `{A : Dec}` parameter. That path doesn't go through
  `getClassParameters` (which is reserved for the case where the *referenced*
  thing is itself a class, e.g. `\func foo (X : SomeClass)` walking `SomeClass`).
- Even when `getClassParameters` did fire (for class-of-class references),
  `myInstanceMap.get(Dec)` was already `<none>` because none of the 9 in-scope
  instances of `dec<_reduce` are classified as Dec/StrictPoset/BiorderedSet —
  Gap 1 doesn't change that outcome.

So Gap 1 wasn't masking the contradiction bug, and fixing it wouldn't have
clarified the diagnosis.

## What it would cause if triggered

A user-visible failure would look like:
- A new source-side definition references a class whose deserialized
  ancestor carries a header `(X : SomeOtherClass)` parameter-field.
- The header chain has a `SomeOtherClass`-classified `\instance` somewhere
  in scope.
- During typechecking, `addInstances(SomeOtherClass, …)` is never called
  (because `getClassParameters` truncated), so that `\instance` gets dropped
  from `Ordering.forDependencies`'s intersection.
- The resulting `GlobalInstancePool` lacks `SomeOtherClass` entries, and the
  user sees `Cannot infer an instance of class 'SomeOtherClass'` at a
  reference site that "should obviously work".

Today this combination is unlikely in arend-lib (parameter-fields are sparse;
the ones that exist — `Iso`, `Mono`, `SplitMono` referencing `Precat` — are
already covered by the source class's *own* `addParametersClassReferences`
walk, not by an inheritance walk). But it's exactly the brittle shape that
trips when someone adds a new header-parameterized class to an upstream
module.

## Sketch of the fix

`getClassParameters` returns `List<Concrete.Parameter>` — we can't synthesize
concrete parameters from core fields cleanly. Two options:

**(a) Side-effect at the truncation point.** When the super has no concrete
but is a core `ClassDefinition`, call `addInstances(...)` directly for its
class-typed fields. Mixes pure-return + side-effect in one helper, but the
side effect already exists in the surrounding code path.

**(b) Supplement at the call site.** `visitReference`'s class branch (line
369-380) calls `getClassParameters(classDef)` + `addParametersClassReferences(...)`.
After that, also walk `classDef`'s core super-chain and, for each deserialized
super, route through `addCoreParameterClassReferences` (or a refactored helper
that just does the field walk without parameter-arg matching). This duplicates
some work for source-only chains but `addInstances` is idempotent via
`myVisitedClasses`.

I'd prefer (b) — it keeps `getClassParameters` honest (pure tree traversal)
and concentrates the "concrete + core" coordination in one place. The cost
is a few hundred wasted lookups per typecheck pass in the common case where
the super-chain is fully source.

## Recommendation

**Don't fix it now.** Reasons:

1. No known repro, and the contradiction case it was suspected of was a red
   herring. Fixing speculatively risks the same "second-order regression"
   pattern that triggered commits `375e79bd2` and `3fffca307`.
2. The fix has architectural choices (a vs. b above) worth thinking through
   when there's a concrete failure to validate against. Picking blind risks
   over-fitting to a synthetic scenario.
3. The two adjacent fallback sites that *do* exist (`fillInstanceMap:146` and
   `addCoreParameterClassReferences:417`) cover most realistic mixed-concrete
   cases. The remaining gap is narrow.

**Do file it.** A one-paragraph note in serialization-maintenance.md (or
wherever the team tracks brittle spots in the serialization layer) pointing
at line 308 with the conditions to trigger. The next person who sees a
"Cannot infer an instance of class X" after touching a `\class Foo (X : SomeClass)`
declaration will need that pointer.

## Related but distinct issues that surfaced during investigation

- **Gap 2** — Java-meta short-circuit in `visitReference:325-343`. Real, but
  the body-null path doesn't currently demand anything: the surrounding
  parameter walk already covers the class refs the meta will need. If a
  future Java meta needs class demands that aren't on the surrounding
  function's signature, we'd need a `MetaDefinition.getDependencyClasses()`
  SPI — but only then.
- **Gap 3** — `BinarySource.setDefinitionListener(...)` has the same "setter
  with zero callers" pathology that `setKeyRegistry` had until just now. If a
  deserialized definition needs `listener.loaded(...)` to run for any
  side-effect, that path is broken the same way the irreflexivity userData
  was. No active failure today; flagging for symmetry.
