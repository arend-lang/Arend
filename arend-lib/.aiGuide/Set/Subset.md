### Set.Subset

Subsets of a type as `\Prop`-valued predicates, equipped with a frame/locale structure, together with operations on subsets, covers, and refinements.

#### Core Types

- **`Set`**: A subset of `X` is a predicate `X -> \Prop`.
- **`Subset`** (alias **`⊆`**): Subset inclusion: `\Pi {x : X} -> U x -> V x`.
- **`Elem`**: The total space of a subset `U`: pairs `(x, U x)` with proof-irrelevant component.
- **`Set.Total`**: Same as `Elem` — sigma of elements satisfying `U`.

#### Basic Constructions

- **`single`**: The singleton subset `{a} = \lam x => a = x`.
- **`single_<=`**: If `U a` holds, then `single a ⊆ U`.
- **`Compl`**: The complement of a subset: `\lam x => Not (U x)`.
- **`^-1`**: Preimage of a subset along a function `f : X -> Y`.
- **`^-1_<=`**: Preimage is monotone with respect to `⊆`.
- **`Set.Prod`**: Product subset of `\Sigma X Y` from subsets of `X` and `Y`.
- **`Set.restrict`**: Restrict a subset `V : Set X` to the total space of `U`.

#### Unions and Joins

- **`Set.Union`**: Union of a family `S : Set X -> \hType` of subsets: `\lam a => ∃ (U : S) (U a)`.
- **`Set.Union-cond`**: Each member of the family is included in the union.
- **`bottom-empty`**: The bottom subset is empty — membership in it implies any proposition.

#### Lattice/Locale Structure

- **`SetLattice`**: Instance making `Set A` into a `Locale` with `⊆` as order, intersection as meet, full set as top, indexed union as `Join`, and the join-distributivity law.

#### Extension and Restriction

- **`extend`**: Extend a subset `V : Set (Total U)` to `Set X` via `\lam x => \Sigma (Ux : U x) (V (x, Ux))`.
- **`extend-sub`**: `extend V ⊆ U`.
- **`extend-mono`**: `extend` is monotone in `V`.
- **`extend_meet`**: `extend` preserves binary meets.
- **`restrict_extend`**: `restrict (extend V) = V` (one direction of the Galois correspondence).
- **`extend_restrict`**: `extend (restrict V) ⊆ V`.

#### Covers and Their Intersections

- **`Set.CoverInter`**: Pairwise intersection of two covers `C, D` — the cover whose members are `U ∧ V` for `U : C`, `V : D`.
- **`Set.CoverInterBig`**: Iterated `CoverInter` over an array of covers, starting from `single top`.
- **`CoverInterBig-char`**: Characterization: members of `CoverInterBig Cs` are exactly meets `Big ∧ top Us` for choice functions `Us` picking one set from each `Cs j`.

#### Refinement of Covers

- **`Refines`**: `C` refines `D` iff every `U : C` is contained in some `V : D`.
- **`Refines-cover`**: If `C` refines `D`, then any cover-witness in `C` lifts to one in `D`.
- **`Refines-single_top`**: Every cover refines the trivial cover `{top}`.
- **`Refines-refl`**: Refinement is reflexive.
- **`Refines-trans`**: Refinement is transitive.
- **`Refines-inter-left`**, **`Refines-inter-right`**: `CoverInter C D` refines each of `C` and `D`.
- **`Refines-inter-big`**: `CoverInterBig Cs` refines each component cover `Cs j`.
- **`Refines-inter`**: Refinement is monotone with respect to `CoverInter` componentwise.

#### Frame Homomorphism

- **`^-1_FrameHom`**: The preimage map `f ^-1 -` is a frame homomorphism from `SetLattice B` to `SetLattice A`, preserving `⊆`, top, finite meets, and arbitrary joins.
