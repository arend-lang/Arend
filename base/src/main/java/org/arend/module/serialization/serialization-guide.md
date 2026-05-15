# Arend ARC Serialization — Maintenance Guide

This document covers the test methodology for serialization/deserialization
work and the small set of invariants you need to keep in mind when touching
this subsystem. Most concrete fixes are described in their commit messages;
this guide is intentionally short.

## The two test scenarios

The two `arend-lib` tests cover complementary scenarios:

### `ArendLibRoundTripTest`

Self-consistency. Typechecks all of `arend-lib` from sources, serializes
every module to a temp directory, deserializes them on a fresh server and
verifies the deserialized core matches the source-typechecked one.

Catches: serialization/deserialization bugs that show up even when nothing
external consumes the serialized output. Misses: bugs that surface only
when fresh typechecking interacts with deserialized prerequisites.

### `ArendLibPartialRoundTripTest`

Cross-pass consistency. Typechecks a target module + its transitive
prerequisites (the *cone*), serializes the cone, then on a fresh server
deserializes the cone and typechecks the *rest* of `arend-lib` from
sources. The test fails on **secondary** typechecking errors — errors
that don't appear in a from-source baseline. With `RUN_BASELINE=false`,
secondary errors are simply errors in modules outside the cone.

Catches: bugs in the contract between deserialized core and the
fresh-typechecking machinery (scope resolution, instance search, ordering,
typing info). This is where most of the subtle deserialization bugs hide.

Run with `./gradlew partialRoundTripTest`. Default target is
`AG.Projective`; override with
`-Darend.partial_roundtrip.targets=Foo.Bar,Baz.Quux`.

## ARD + ARC overlay (the test loads both for cone modules)

`ArendLibPartialRoundTripTest` Phase 3 loads each cone module twice:

1. **`.ard` source** is parsed first — the resulting `ConcreteGroup` carries
   the full concrete tree (including inline `\meta` bodies, which are not
   serialized into `.arc`).
2. **`.arc` binary** is overlaid on the same group via
   `ModuleDeserialization.parseProtobuf` + `readDefinitions(group)` +
   `readModule(scopeProvider, depCol)`. This populates `setTypechecked()`
   on the source-parsed referables with the deserialized core, without
   creating fresh referables.

Cyclic imports (e.g. `Equiv ↔ Equiv.Fiber`) are handled transparently:
`readModule` recursively calls `findModule` which calls
`requestModuleUpdate` on the hybrid requester, which serves cone deps
the same way (ARD then ARC overlay). Non-cone modules go through ARD
only. The test pre-loads cone modules in topological order to minimise
recursion depth, but cycles still resolve correctly because the
ARD-loaded group is registered before its `readModule` is invoked.

This is *test-side* behaviour. Production code does not parse sources
during deserialization, and there is no `loadSourceGroup` requester
method. The overlay approach exists only because the test exercises a
scenario where source is still available.

## The one invariant

**For a deserialized `ConcreteGroup` (the result of
`ModuleDeserialization.readGroup`), `group.definition()` is always
`null`.** Concrete is not a core entity and is not serialized.

Any code path that branches on
`group.definition() instanceof Concrete.ClassDefinition`/`DataDefinition`/
`FunctionDefinition` silently does nothing for deserialized groups. The
fallback pattern is to read the same shape off
`group.referable().getTypechecked()` (the core `Definition`).

This is not relevant to the test scenario above — the test loads ARD on
top, so `group.definition()` is non-null for cone modules. But for any
other consumer of `ModuleDeserialization` (IDE caches, future tooling),
the invariant holds and the fallbacks are required.

## When adding a field to a proto

1. Add the field to `proto/src/main/proto/*.proto`.
2. Update the serializer (`DefinitionSerialization.java` /
   `ModuleSerialization.java`).
3. Update the deserializer (`ModuleDeserialization.java`). Note that
   `readGroup` and `readDefinitions` take different paths and may both
   need updating.
4. The default proto value (0 / `""` / empty) must produce behaviour
   compatible with `.arc` files written before the field was added.
5. If the new field affects scope visibility, precedence, or instance
   matching, run `ArendLibPartialRoundTripTest`. Scope bugs surface there
   much earlier than in `ArendLibRoundTripTest` (which only tests
   self-consistency, not interaction with fresh typechecking).

## Known issues

Unfixed brittle spots in the layer. None have a known repro in arend-lib
today; they're recorded so that the next person who sees the matching
symptom has a pointer instead of starting cold.

### `getClassParameters` truncates at deserialized super-classes

`CollectDefCallsVisitor.getClassParameters` walks a class's super-chain
collecting **parameter-fields** (fields from a class header like
`\class Map (C : Precat) { … }`) and feeds their types into
`addParametersClassReferences`, which calls `addInstances(...)` for any
class-typed parameter type. The super-class loop silently drops any super
whose concrete is `null` — i.e. supers loaded from `.arc`. Two sibling
sites in the same file (`fillInstanceMap` and
`addCoreParameterClassReferences`) already carry the deserialized
fallback; `getClassParameters` is the lone unpatched site.

Symptom if triggered: `Cannot infer an instance of class X` at a
reference site where a source-side class extends a deserialized class
that carries a class-typed header parameter, with an `X`-classified
`\instance` in scope.

Not fixed because parameter-fields are sparse in arend-lib and the
realistic instances (`Iso`/`Mono`/`SplitMono` referencing `Precat`) are
already covered by the source class's own `addParametersClassReferences`
walk. The fix also has an architectural choice worth thinking through
when there's a concrete failure to validate against:
`getClassParameters` returns concrete parameters, and the deserialized
side has only core fields — either side-effect at the truncation point
(call `addInstances` directly for class-typed core fields) or supplement
at the call site (walk the core super-chain after the concrete walk).

### Java-meta short-circuit in `visitReference`

`CollectDefCallsVisitor.visitReference`'s `metaRef` branch only walks
the meta's concrete `body`. When `body` is `null` (Java-implemented
metas without a concrete body), the branch returns without exploring
any dependencies. The surrounding parameter walk currently covers
everything those metas demand, so the gap is dormant. Fixing properly
would require a `MetaDefinition.getDependencyClasses()` SPI — defer
until a Java meta actually demands classes that aren't on its
surrounding function's signature.

### `BinarySource.setDefinitionListener` has zero callers

`BinarySource.setDefinitionListener` is declared and implemented in
`StreamBinarySource`, but no production caller wires it up. This is
the same "setter with no callers" shape that `setKeyRegistry` had
before being plumbed in — flagged for symmetry. If a deserialized
definition ever needs the listener's `loaded(...)` callback to fire
for a side effect, that path is currently broken the same way the
irreflexivity userData was before the key-registry plumb-through.
