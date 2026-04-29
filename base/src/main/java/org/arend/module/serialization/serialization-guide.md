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

## Diagnostic system properties

All gated on system properties; zero cost when unset.

| Flag | Effect |
|---|---|
| `-Darend.subst.maxDepth=N` | `SubstVisitor.visitPi` throws `SubstDepthExceeded` at recursion depth N instead of blowing the stack. |
| `-Darend.instance.maxDepth=N` | `GlobalInstancePool.findInstance` throws `InstanceDepthExceeded` when instance-search recursion exceeds N. |
| `-Darend.ordering.probeOrder=<substr>` | `Ordering.order` logs every `order(def)` call whose long name contains the substring, with typechecked state and SKIP/REORDER decision. |

These are intentionally permanent. They cost nothing when their flags are
unset and pay for themselves the first time a deserialization-induced
infinite recursion or substitution cycle needs diagnosing.
