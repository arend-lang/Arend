### Topology.CStarAlgebra.CompleteStoneCStarAlgebra

Completion of ordered C*-algebras into Stone C*-algebras and the universal property of dense isometric embeddings.

This module bridges the gap between (possibly incomplete) ordered C*-algebras and their completions as Stone C*-algebras (commutative real C*-algebras with bounded suprema). The central construction takes an `OrderedC*Algebra` and produces its Banach-algebra completion, equipping it with the additional Stone C*-algebra structure (commutativity, the C*-identity, and the sum-of-squares positivity axiom). The universal property is realized by `dense-c*-lift`, which extends a homomorphism along a dense isometric embedding by lifting the underlying normed-space map and verifying multiplicativity via density of the product. Order-preserving C*-algebra homomorphisms are shown to be automatically norm-bounded, linking the algebraic/order side with the metric/topological side.

#### Homomorphism Records

- **`OrderedC*AlgebraHom`**: Extends `RingHom` and `PosetHom` between `OrderedC*Algebra`s — a ring homomorphism that also preserves the order. Carries the hooks `IsDense`, `IsIsometric`, and conversion lemmas to the underlying normed map.
- **`StoneC*AlgebraHom`**: Extends `OrderedC*AlgebraHom` and `BoundedLinearMap` for maps between `StoneC*Algebra`s. The order-preserving and bounded-linear data are derived automatically: `func-<=` follows by writing `y - x` as a square, and `isBounded` uses the C*-norm characterization to give bound `1`.

#### Density and Isometry Properties

- **`OrderedC*AlgebraHom.IsDense`**: For every `eps > 0` and target `y`, there exists `x` in the domain with `func x` within `eps · 1` of `y` on both sides — order-theoretic density via the unit `1`.
- **`OrderedC*AlgebraHom.IsIsometric`**: If `func x` is bounded by `1` in absolute value, then `x ≤ q · 1` for every rational `q > 1` — an order-theoretic formulation of being norm-non-expanding.
- **`OrderedC*AlgebraHom.toNormed`**: Reinterprets the homomorphism as a `NormedAbGroupMap` between the underlying Banach spaces.
- **`OrderedC*AlgebraHom.dense-toNormed`**: `IsDense` is equivalent to density of `toNormed` as a normed-space map.
- **`OrderedC*AlgebraHom.isometry-toNormed`**: `IsIsometric` is equivalent to the literal norm equality `‖func x‖ = ‖x‖`.
- **`OrderedC*AlgebraHom.toIsometry`**: Promotes an `IsIsometric` homomorphism to a `NormedIsometricMap`.

#### Construction Lemmas

- **`OrderedC*AlgebraHom.fromNormed`**: Builds an `OrderedC*AlgebraHom` from a ring homomorphism `f : X -> Y` (with `Y` a `StoneC*Algebra`) given the norm-domination hypothesis `‖f x‖ ≤ ‖x‖`. Uses the C*-norm to recover the order condition.
- **`StoneC*AlgebraHom.fromRingHom`**: Any ring homomorphism between Stone C*-algebras is automatically a Stone C*-algebra homomorphism (orders and norms are determined algebraically).

#### Universal Lift

- **`dense-c*-lift`**: Given `f : X -> Y` a dense isometric `OrderedC*AlgebraHom` and `g : X -> Z` into a Stone C*-algebra, produces a Stone-target homomorphism `Y -> Z` extending `g`. Built by first lifting `g.toNormed` along the normed dense isometry and then transferring multiplicativity using density of the product `X × X` in `Y × Y` together with continuity of `*`.
- **`dense-c*-lift.char`**: The lifted map satisfies `dense-c*-lift f fd fi g (f x) = g x` — confirms it extends `g` along `f`.

#### Completion Instance

- **`StoneC*AlgebraCompletion`**: Instance turning the Banach-algebra completion of an `OrderedC*Algebra X` into a `StoneC*Algebra`. Inherits the Banach algebra structure from `BanachAlgebraCompletion X.toBanach` and supplies the missing axioms `*-comm`, `c*-sum`, and `c*-square` by extending the corresponding properties of `X` along the dense embedding into the completion.
