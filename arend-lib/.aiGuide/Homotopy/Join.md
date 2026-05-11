### Homotopy.Join

The join construction `A * B` of two types as a higher inductive type, realized as a pushout of the projections from `A × B`.

The join is defined as `PushoutData {Σ A B} pr₁ pr₂`, gluing each `a : A` to each `b : B` via a path. Key results show the join is symmetric, associative, contractible when one factor is contractible, and propositional when both factors are propositions. The interaction with spheres and suspensions is the central application: `Join (Sphere 0) A ≃ Susp A`, which combined with associativity yields `Join (Susp A) B = Susp (Join A B)` and ultimately `Join (Sphere n) A = iterated suspension`.

#### Core Definition

- **`Join`**: The join `A * B` defined as `PushoutData {Σ A B} __.1 __.2`, gluing `a` to `b` along the projections.
- **`Join.levelProp`**: When `A B : \Prop`, the join is itself a proposition.
- **`Join.from-||`**: Converts a propositional truncation `A || B` into `Join A B` (using `levelProp`).

#### Constructors

- **`jinl`**: Left inclusion `A -> Join A B`.
- **`jinr`**: Right inclusion `B -> Join A B`.
- **`jglue`**: The gluing path `jinl a = jinr b` for any `a : A`, `b : B`.

#### Contractibility

- **`Join_Contr`**: If `B` is contractible, then `Join A B` is contractible for any `A`.

#### Equivalence with Suspension

- **`Join_Sphere0`**: `QEquiv {Join (Sphere 0) A} {Susp A}` — joining with the 0-sphere produces the suspension.
  - **`joinSusp`**, **`suspJoin`**: The forward and backward maps; `suspJoin` sends a meridian `pglue a` to `jglue north a *> inv (jglue south a)`.
  - **`joinSuspJoin`**, **`suspJoinSusp`**: The two homotopies witnessing it is a quasi-equivalence, built using `Cube2` to fill the required squares on path constructors.

#### Symmetry

- **`Join-sym`**: `QEquiv {Join A B} {Join B A}` — the join is symmetric.
  - **`Join-sym.flip`**: Swaps `jinl`/`jinr` and inverts the gluing path.
  - **`Join-sym.flip-flip`**: Witnesses `flip ∘ flip = id` via `pmap_inv-comm` and `inv_inv`.

#### Associativity

- **`Join-assoc`**: `QEquiv {Join (Join A B) C} {Join A (Join B C)}` — associativity of the join.
  - **`Join-assoc.leftToRight`**, **`Join-assoc.rightToLeft`**: The two reassociation maps, defined by case analysis on each pushout layer; the doubly-glued case `pglue (pglue (a, b), c)` is filled using `Jl` over `jglue b c` (resp. `jglue a b`) with a `Cube2`.
  - **`Join-assoc.leftRightLeft`**, **`Join-assoc.rightLeftRight`**: The round-trip homotopies; the deepest case requires comparing two cube fillings via a `Jl`-based identity plus an explicit `coe` computation.

#### Suspension and Sphere Joins

- **`Join_Susp`**: Equality `Join (Susp A) B = Susp (Join A B)`, derived by combining symmetry, `Join_Sphere0`, and associativity through `QEquiv-to-=`.
- **`Join_Sphere`**: For all `n : Nat`, `Join (Sphere n) A = iterr Susp (suc n) A`; proved by induction on `n` using `Join_Sphere0` at the base and `Join_Susp` at the step.
