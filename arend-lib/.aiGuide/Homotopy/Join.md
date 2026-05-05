### Homotopy.Join

The join of two types `A * B`, defined as the pushout of the projections `A <- A × B -> B`, with equivalences relating it to suspensions, spheres, and itself under symmetry and associativity.

#### Core Definition

- **`Join`**: The join `A * B` defined as `PushoutData {\Sigma A B} __.1 __.2` — the homotopy pushout of the two projections.
- **`Join.levelProp`**: The join of two propositions is itself a proposition.
- **`Join.from-||`**: Converts a propositional disjunction `A || B` into `Join A B` (when `A` and `B` are propositions).

#### Constructors

- **`jinl`**: Left inclusion `A -> Join A B`.
- **`jinr`**: Right inclusion `B -> Join A B`.
- **`jglue`**: The path `jinl a = jinr b` joining any two points.

#### Contractibility

- **`Join_Contr`**: The join `Join A B` is contractible whenever `B` is contractible.

#### Join with the 0-Sphere (Suspension)

- **`Join_Sphere0`**: Equivalence `Join (Sphere 0) A ≃ Susp A`, exhibiting suspension as the join with the 0-sphere.
  - **`joinSusp`**: Forward map sending the join into the suspension.
  - **`suspJoin`**: Inverse map sending the suspension into the join, using `jglue north a *> inv (jglue south a)` for the meridian.
  - **`joinSuspJoin`**, **`suspJoinSusp`**: The two homotopies witnessing the equivalence.

#### Symmetry

- **`Join-sym`**: Equivalence `Join A B ≃ Join B A`.
  - **`flip`**: The symmetry map swapping the two sides, sending `jglue a b` to `inv (jglue b a)`.
  - **`flip-flip`**: `flip` is its own inverse.

#### Associativity

- **`Join-assoc`**: Equivalence `Join (Join A B) C ≃ Join A (Join B C)`.
  - **`leftToRight`**, **`rightToLeft`**: The associator maps in each direction.
  - **`leftRightLeft`**, **`rightLeftRight`**: Homotopies establishing the equivalence, built using cube fillers and `Jl` over the gluing paths.

#### Iterated Suspension

- **`Join_Susp`**: Equality `Join (Susp A) B = Susp (Join A B)`, derived from symmetry, the 0-sphere case, and associativity.
- **`Join_Sphere`**: Equality `Join (Sphere n) A = iterr Susp (suc n) A`, expressing the join with an `n`-sphere as the `(n+1)`-fold iterated suspension.
