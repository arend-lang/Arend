### Set.Subset

Subsets of a type encoded as predicates `X -> \Prop`, with their lattice/locale structure and tools for refinements and covers.

This module identifies a subset of `X` with a propositional predicate, so set-theoretic operations (intersection, union, complement, preimage) become logical operations on predicates. The powerset `Set X` is given the structure of a `Locale` (`SetLattice`), where `<=` is pointwise implication `⊆`, joins are existential quantifiers, and meets are pairwise conjunctions; this lets the rest of the library reuse frame/locale machinery on plain subsets. The module also develops the language of *covers* (sets of subsets) with a refinement preorder and the `CoverInter` operation, which is the key combinatorial primitive used by topology/locale-style proofs in the library. Finally, `extend`/`restrict` mediate between subsets of `X` lying inside a fixed `U` and subsets of the total space `Total U`, and `^-1` packages preimage as a frame homomorphism.

#### Core Definitions

- **`Set`**: A subset of `X` is a predicate `X -> \Prop`.
- **`Elem`**: The total space of a subset: `\Sigma (x : X) (U x)`, the type of elements satisfying `U`.
- **`Set.Total`**: Same as `Elem`, packaged inside the `Set` namespace.
- **`single`**: The singleton subset `single a = (a =)`.
- **`Subset`** / **`⊆`**: Inclusion of subsets, defined as pointwise implication.
- **`Compl`**: Pointwise complement: `Compl U x = Not (U x)`.
- **`^-1`**: Preimage of a subset along a function `f : X -> Y`.

#### Constructions on Subsets

- **`Set.Union`**: Union of a family `S : Set X -> \hType` of subsets, `∃ (U : S) (U a)`.
- **`Set.restrict`**: Restriction of a subset `V` to the total space `Total U`.
- **`Set.Prod`**: Product of subsets `U ⊆ X`, `V ⊆ Y` as a subset of `\Sigma X Y`.
- **`extend`**: Extends a subset `V` of `Total U` to a subset of `X` (those `x` in `U` whose pair lies in `V`).

#### Lattice / Locale Structure

- **`SetLattice`**: Instance making `Set A` into a `Locale`: `<=` is `⊆`, `meet` is pointwise conjunction, `top` is the full set, `Join` is the existential quantifier; distributivity makes it a frame.
- **`single_<=`**: `single a ⊆ U` whenever `U a`.
- **`bottom-empty`**: An element of the bottom subset gives any proposition (ex falso).

#### Extend / Restrict Lemmas

- **`extend-sub`**: `extend V ⊆ U`: extending stays inside `U`.
- **`extend-mono`**: `extend` is monotone in `V`.
- **`extend_meet`**: `extend` preserves meets: `extend (V ∧ W) = extend V ∧ extend W`.
- **`restrict_extend`**: `restrict (extend V) = V` — `extend` is a section of `restrict`.
- **`extend_restrict`**: `extend (restrict V) ⊆ V` — the other composite is a retraction up to inclusion.

#### Preimage as a Frame Homomorphism

- **`^-1_<=`**: Preimage is monotone: `U ⊆ V` implies `f ^-1 U ⊆ f ^-1 V`.
- **`^-1_FrameHom`**: Packages `f ^-1` as a `FrameHom (SetLattice B) (SetLattice A)`: it preserves order, top, meets, and joins.

#### Covers and Intersection of Covers

- **`Set.CoverInter`**: Pairwise intersection of two covers: all `U ∧ V` with `U ∈ C`, `V ∈ D`.
- **`Set.CoverInterBig`**: Iterated `CoverInter` over an array of covers, starting from `single top`.
- **`CoverInterBig-char`**: Characterizes elements of `CoverInterBig Cs` as big meets `Big ∧ top Us` of choices `Us j ∈ Cs j`.

#### Refinement of Covers

- **`Refines`**: `C` refines `D` iff every `U ∈ C` is contained in some `V ∈ D`.
- **`Refines-cover`**: A refinement transports pointwise coverage: if some `U ∈ C` contains `x`, so does some `V ∈ D`.
- **`Refines-single_top`**: Every cover refines the trivial cover `{top}`.
- **`Refines-refl`**: Refinement is reflexive.
- **`Refines-trans`**: Refinement is transitive.
- **`Refines-inter-left`** / **`Refines-inter-right`**: `CoverInter C D` refines both `C` and `D`.
- **`Refines-inter-big`**: `CoverInterBig Cs` refines each component cover `Cs j`.
- **`Refines-inter`**: Refinement is monotone under `CoverInter`: `C ≤ D`, `C' ≤ D'` implies `CoverInter C C' ≤ CoverInter D D'`.
