### Order.LinearOrder

Total and linear orders, with constructive variants based on a strict order plus comparison and connectedness.

This module formalizes orders where any two elements are comparable. `TotalOrder` extends `DistributiveLattice` and derives meets/joins from a `totality` axiom by selecting one of the two compared elements. `LinearOrder` is the constructive presentation built on a strict order `<` with `<-comparison` (a strict counterpart of trichotomy that locates a third point) and `<-connectedness` (no element strictly differs in either direction implies equality); from these, the non-strict order is defined as `Not (a' < a)`. The decidable variant `LinearOrder.Dec` provides a full `Tri` trichotomy that yields decidable equality, lattice operations by case analysis, and constructive minimum/maximum search over arrays. Density and unboundedness refinements are layered on top.

#### Total Order

- **`TotalOrder`**: Class extending `DistributiveLattice` with `totality : x <= y || y <= x`. Default meet/join, lattice laws, and the distributivity inequality `ldistr>=` are derived from `totality` via the `tmeet`/`tjoin` helpers.
- **`tmeet`**, **`tjoin`** (in `\where`): Build the meet/join of two elements from a proof `x <= y || y <= x` by selecting the smaller/larger one and proving the universal property.
- **`meet-isMin`**: For any `x y`, `x ∧ y = x` or `x ∧ y = y`.
- **`meet-prop`**: A property `P` closed under both `x` and `y` holds at `x ∧ y` (since meet picks one).
- **`Big01_meet-isMin`**: For arrays, `Big ∧ x l` equals either some `l j` or the seed `x`.
- **`Big_meet-isMin`**: For nonempty arrays, `Big_∧ l = l j` for some index `j`.
- **`join-isMax`**, **`join-prop`**, **`Big01_join-isMax`**, **`Big_join-isMax`**: Dual statements for joins.

#### Linear Order (Strict-Based)

- **`LinearOrder`**: Class extending `BiorderedSet`, axiomatized by `<-comparison y : x < z -> x < y || y < z` and `<-connectedness : Not (x < y) -> Not (y < x) -> x = y`. The non-strict order, reflexivity, transitivity, antisymmetry, and the bidirectional `<-transitive-*` laws are all derived.
- **`LinearOrder.op`**: The opposite linear order, swapping `<`.
- **`<=` (in `\where`)**: Defined as `Not (a' < a)` for any strict poset.
- **`<_<=`**: A strict inequality implies the non-strict one.
- **`notLess`**: `a <= a'` and `a' < a` are contradictory.

#### Tight Apartness Variant

- **`LinearOrder.With#`**: Linear order with the apartness relation `x # y := x < y || y < x`, providing irreflexivity, symmetry, comparison, and tightness, so it extends `Set#`.

#### Decidable Linear Order

- **`LinearOrder.Dec`**: Class extending `LinearLattice`, `DecSet`, and `TotalOrder`, axiomatized by trichotomy `Tri x y`. Derives `<-comparison`, `<-connectedness`, `totality`, `decideEq`, and lattice operations by case-splitting on `trichotomy`.
- **`LinearOrder.Dec.op`**: Opposite decidable linear order.
- **`<-dec`**: Decidability of `a < a'`.
- **`<=_/=`**: From `a <= a'` and `a /= a'`, derive `a < a'`.
- **`<=-dec`**: A non-strict `a <= a'` resolves to either `a < a'` or `a = a'`.
- **`dec<_<=`**: Decides between `a < a'` and `a' <= a`.
- **`dec<_reduce`**, **`dec<=_reduce`**: Compute `dec<_<=` to its expected branch given the witness.
- **`dec<=`**: Decidability of `a <= a'`.
- **`trichotomy<_reduce`**, **`trichotomy=_reduce`**, **`trichotomy>_reduce`**: Force `trichotomy` to the appropriate constructor when the relevant proof is in hand.

#### Lattice Interaction Lemmas

- **`meet/=left`**: If `a ∧ b /= a`, then `a ∧ b = b`.
- **`meet/=right`**: If `a ∧ b /= b`, then `a ∧ b = a`.
- **`join/=left`**: If `a /= a ∨ b`, then `a ∨ b = b`.
- **`join/=right`**: If `b /= a ∨ b`, then `a ∨ b = a`.

#### Min/Max Search

- **`findMin`**: For a nonempty array over a `Dec` linear order, returns an index `j` with `Big_∧ l = l j`.
- **`findMax`**: Dual: returns an index witnessing `Big_∨ l = l j`.

#### Monotonicity

- **`monotone-injective`**: A strictly monotone map from a linear order to a strict poset is injective.

#### Linear Lattice

- **`LinearLattice`**: Class extending `LinearOrder` and `BiorderedLattice`, supplying `<_meet-univ` and `<_join-univ` (strict universal properties of meet/join) by case analysis on `<-comparison`.

#### Density and Unboundedness

- **`DenseLinearOrder`**: Linear order with `isDense : x < z -> ∃ y, x < y < z`. Has a decidable subclass `DenseLinearOrder.Dec`.
- **`UnboundedDenseLinearOrder`**: Adds `withoutUpperBound` and `withoutLowerBound`. Has a decidable subclass `UnboundedDenseLinearOrder.Dec`.

#### Trichotomy Datatype

- **`Tri`**: Three-way comparison data `less (a < a') | equals (a = a') | greater (a > a')`, with a `\use \level` instance making it a proposition (since the three cases are mutually exclusive in a strict poset).
