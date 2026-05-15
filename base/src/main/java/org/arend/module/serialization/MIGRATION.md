# Serialization: Migration Considerations

This document discusses the pain points of the current protobuf-based ARC
serialization and evaluates alternative frameworks that could reduce maintenance
burden as the Arend core evolves.

## Problems with the current approach

### 1. Manual binding-identity tracking

The central difficulty is not the wire format — it is that **binding identity
must be preserved across the round-trip**.  The serializer maintains an explicit
`Map<Binding, Integer>` and every expression type that introduces a binding
(`Pi`, `Lambda`, `Sigma`, `Let`, `Case`, `ClassCall`, `AbsExpression`) must
register it in the correct order, detect sharing (`existing_ref`), and emit the
right index.  The deserializer must recreate bindings in exactly the same order
and resolve indices back to Java objects.

Each new expression or binding type requires coordinated changes in four files
(serializer, deserializer, proto schema, and test), with subtle invariants:
- The same `DependentLink` chain may be shared between a `Pi` and its `Lambda`.
- A `ClassCallBinding` is an inner class of `ClassCallExpression`; sharing the
  same Java object is semantically required.
- DAG sharing (`LetExpression`, `ClassCallExpression`) must be detected to avoid
  re-registration that overwrites binding indices.

Bugs in this machinery produce errors that are hard to diagnose — "Variable X
is not bound", "Type mismatch" from binding-index shifts, or silent data
corruption that only manifests when the definition is used in a proof.

### 2. Fragile schema evolution

The `.proto` schema mirrors the Java class hierarchy almost 1-to-1.  Any change
to the core expression types (adding a field, changing a variant, reordering
constructor arguments) requires a matching proto change plus explicit
serialization/deserialization code.  There is no automatic compatibility layer:
the `VERSION` constant is bumped and all existing `.arc` files are invalidated.

### 3. Expression sharing is not preserved generically

The serializer only detects sharing for specific types (`ClassCallExpression`,
`LetExpression`, `DependentLink` chains).  General expression DAG sharing (e.g.,
two `FunCallExpression` nodes that are the same Java object) is silently
duplicated.  This causes comparison failures in the core checker
(`CompareVisitor` relies on `expr1 == expr2` shortcuts) and inflates file size.

### 4. Pretty-printer coupling

The round-trip test relies on `ToAbstractVisitor` for structural comparison.
Any change to the pretty-printer can mask or introduce false positives.  A
binary structural comparator (walking the expression tree directly) would be
more robust but is not feasible with the current approach because binding
identity differs after deserialization.

## Evaluation of alternative frameworks

### Option A: Stay with protobuf, add generic expression deduplication

Keep the existing proto schema but add a generic expression-identity map:
assign each expression node a serial number on first visit; on re-visit, emit
a back-reference.  This is essentially what Cap'n Proto and FlatBuffers do for
object graphs.

**Pros:** Minimal schema changes; fixes the sharing problem; no new dependency.
**Cons:** Still requires manual binding tracking; schema evolution is still
manual; doesn't reduce the coordination burden across four files.

### Option B: Java built-in serialization (ObjectOutputStream)

Replace protobuf with `java.io.Serializable` / `Externalizable`.

**Pros:** Zero schema maintenance; sharing and cycles handled automatically;
binding identity preserved by default (the JVM serializer uses an identity map).
**Cons:** Fragile across class changes (serialVersionUID); terrible performance
and file size; security concerns (deserialization attacks); no cross-language
support; no schema documentation.

**Verdict:** Not suitable.

### Option C: kryo / fury

High-performance Java serialization libraries ([kryo](https://github.com/EsotericSoftware/kryo),
[fury](https://github.com/apache/fury)).  They serialize arbitrary object
graphs with identity tracking, handle cycles, and are 5-10x faster than Java
serialization.

**Pros:**
- **Automatic binding-identity preservation.** Both libraries serialize object
  graphs by reference — if two fields point to the same `DependentLink` object,
  the deserialized graph has the same sharing.  This eliminates the entire
  `existing_ref` / binding-map machinery.
- **Expression DAG sharing for free.** All expression sharing is preserved,
  fixing the `CompareVisitor` comparison issue.
- **Minimal code.** Registration of serializers for each class replaces ~1500
  lines of manual serialization/deserialization code.  Schema evolution can be
  handled via `@Since` annotations (fury) or `CompatibleFieldSerializer` (kryo).
- **Small file size.** Variable-length encoding, reference compression; smaller
  than protobuf for deeply nested trees.

**Cons:**
- Java-only (no cross-language `.arc` files).
- Class renames / field reordering break compatibility without explicit version
  handling.
- No human-readable schema (harder to document the wire format).
- New dependency (~300-500 KB).

**Verdict:** **Best fit for Arend's use case.** The core classes change
frequently, and the dominant maintenance cost is binding-identity tracking —
which these libraries eliminate entirely.

### Option D: FlatBuffers / Cap'n Proto

Schema-based binary formats with zero-copy deserialization.

**Pros:** Cross-language; zero-copy reads; schema evolution via field IDs.
**Cons:** Object-graph sharing is not built-in (must be implemented manually,
same as protobuf); generated code is verbose; no automatic binding-identity
preservation.

**Verdict:** Does not solve the core problem.

### Option E: Custom binary format with expression deduplication

Design a custom encoder that walks the expression tree, assigns IDs to every
node (including bindings), and emits a flat array of tagged nodes.
Deserialization reconstructs the graph by resolving IDs.

**Pros:** Full control; optimal size; binding identity preserved by construction.
**Cons:** Significant up-front engineering; no schema documentation; no
community tooling; maintenance burden shifts from proto ↔ Java coordination to
format-spec ↔ Java coordination.

**Verdict:** Viable but high initial cost.  Only worthwhile if neither kryo/fury
nor protobuf-with-dedup meets performance requirements.

## Recommendation

**Short-term:** Add generic expression deduplication to the current protobuf
serializer (Option A).  This fixes the `CompareVisitor` sharing issue and
reduces the number of `existing_ref` special cases needed.

**Medium-term:** Migrate to **kryo** or **fury** (Option C).  The migration
path is:
1. Write kryo/fury serializers for each core expression/definition class.
2. Run the round-trip test against both the old (protobuf) and new (kryo)
   backends to verify equivalence.
3. Bump the ARC version and switch the default backend.
4. Remove the protobuf serialization code (~3000 lines across 4 files).

The primary benefit is eliminating the binding-identity machinery — the single
largest source of serialization bugs.  Secondary benefits include smaller file
size, faster serialization, and simpler code for new expression types (register
the class, done).
