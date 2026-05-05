### Topology.CoverSpace.Subspace

Constructions of cover space structures on subspaces, for both open and closed subsets of a (complete) cover space.

#### Open Subspace Cover Structure

- **`OpenCoverSpace`**: Given a cover space `X` and an open subset `S ⊆ X`, equips `Total S` with a `CoverSpace` structure built as the closure of basic covers (either restrictions of cauchy covers from `X`, or regular-closure covers refining `{S}`).
- **`OpenCoverSpace.isBasicCover`**: Predicate identifying a cover `C` of `Total S` as basic — i.e., `C = {restrict V | V ∈ D}` for some `D` that is either cauchy in `X` or in the regular closure of `{{S}}`.
- **`OpenCoverSpace.basicCover-cover`**: Every basic cover actually covers each point of `Total S`.
- **`OpenCoverSpace.basicCover-regular`**: Basic covers admit regular refinements within `isBasicCover`'s closure.
- **`OpenCoverSpace.makeBasicCover`**: Constructor producing a closure-cover `{restrict V | V ∈ D}` from a witness that `D` is cauchy in `X` or in the regular closure of `{{S}}`.

#### Regular Closure of Cover Sets

- **`RegularClosure`**: Inductive predicate generating, from a property `A` on cover families, all cover families reachable by iterated `<=<`-refinement (`V` being well-inside some `U ∈ D`).
- **`RegularClosure.regular-closure`**: Base constructor: any `A`-cover is in the regular closure.
- **`RegularClosure.regular-closure-extends`**: Step constructor: refinement by `<=<` preserves membership.
- **`closure-neighborhood`**: If every `A`-cover has a member `<=<`-containing `{x}`, then so does every cover in `RegularClosure A`. Used to propagate neighborhood-witnesses through the closure.

#### Open Subspace Maps and Conversion

- **`OpenCoverSpace.func`**: The inclusion `Total S ↪ X` as a `PrecoverMap` from `OpenCoverSpace X So` to `X`.
- **`OpenCoverSpace.<=<-conv`**: Lifts a well-inside relation in the subspace to one in `X`: if `{x'} <=< U'` in `OpenCoverSpace X So`, then `{x'.1} <=< extend U'` in `X`.

#### Filter Extension to Ambient Space

- **`filter-extend`**: Pushes a `SetFilter` on `Total U` forward to a `SetFilter` on `X` by taking preimages under the first projection.
- **`proper-filter-extend`**: Same construction preserving properness, producing a `ProperFilter`.
- **`cauchy-filter-extend`**: Extends a `CauchyFilter` on `OpenCoverSpace X So` to a `CauchyFilter` on the ambient `X`. Used to transport completeness arguments between the subspace and `X`.

#### Complete Subspaces

- **`OpenCompleteCoverSpace`**: When `X` is a complete cover space and `S` is open, `Total S` inherits a `CompleteCoverSpace` structure (separation comes via the inclusion into `X`, completeness from extending cauchy filters).
- **`ClosedCompleteCoverSpace`**: When `S ⊆ X` is closed in a complete cover space, `Total S` is a complete cover space whose cover structure is the transferred one along the inclusion `__.1 : Total S → X`.
- **`ClosedCompleteCoverSpace.conv`**: Converse direction — if the transferred cover space on `Total S` is complete (in a separated `X`), then `S` is closed in `X`. Characterizes closedness via completeness of the transfer.
