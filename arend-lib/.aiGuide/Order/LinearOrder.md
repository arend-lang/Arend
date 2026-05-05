### Order.LinearOrder

Total and linear orders, including decidable, dense, and unbounded variants, with associated lattice structure.

#### Total Orders

- **`TotalOrder`**: Class extending `DistributiveLattice` with a `totality` axiom asserting `x <= y || y <= x`. Meet and join are inherited from the lattice; distributivity is derived from totality.
- **`TotalOrder.tmeet`**: Constructs the meet of `x, y` in any `Poset` given `x <= y || y <= x` — picks the smaller element.
- **`TotalOrder.tjoin`**: Constructs the join of `x, y` in any `Poset` given `x <= y || y <= x` — picks the larger element.

#### Linear Orders

- **`LinearOrder`**: Class extending `BiorderedSet` with `<-comparison` (for any `y`, `x < z` implies `x < y || y < z`) and `<-connectedness` (`Not (x < y) -> Not (y < x) -> x = y`). Derives `<=`, reflexivity, transitivity, antisymmetry, and the relationship between `<` and `<=`.
- **`LinearOrder.With#`**: Class extending `LinearOrder` and `Set#`, defining apartness `x # y` as `x < y || y < x` and proving its irreflexivity, symmetry, comparison, and tightness.
- **`LinearOrder.<=`**: Definition of `<=` from a `StrictPoset`: `Not (a' < a)`.
- **`LinearOrder.<_<=`**: `a < a'` implies `a <= a'`.
- **`LinearOrder.notLess`**: Contradiction from `a <= a'` and `a' < a`.

#### Decidable Linear Orders

- **`LinearOrder.Dec`**: Class extending `LinearLattice`, `DecSet`, and `TotalOrder`, axiomatized by a `trichotomy` field returning `Tri x y` (less, equals, or greater). Derives `<-comparison`, `<-connectedness`, `totality`, and `decideEq`.
- **`LinearOrder.<-dec`**: Decidability of `a < a'`.
- **`LinearOrder.<=_/=`**: `a <= a'` and `a /= a'` imply `a < a'`.
- **`LinearOrder.<=-dec`**: From `a <= a'` produces either `a < a'` or `a = a'`.
- **`LinearOrder.dec<_<=`**: For any `a, a'` produces either `a < a'` or `a' <= a`.
- **`LinearOrder.dec<_reduce`**, **`LinearOrder.dec<=_reduce`**: Reduction lemmas characterizing the output of `dec<_<=` on the two cases.
- **`LinearOrder.trichotomy<_reduce`**, **`LinearOrder.trichotomy=_reduce`**, **`LinearOrder.trichotomy>_reduce`**: Reduction lemmas characterizing the output of `trichotomy` given a witness in each case.
- **`LinearOrder.dec<=`**: Decidability of `a <= a'`.

#### Lattice Operations under Linearity

- **`LinearOrder.meet/=left`**: If `a ∧ b /= a` then `a ∧ b = b`.
- **`LinearOrder.meet/=right`**: If `a ∧ b /= b` then `a ∧ b = a`.
- **`LinearOrder.join/=left`**: If `a /= a ∨ b` then `a ∨ b = b`.
- **`LinearOrder.join/=right`**: If `b /= a ∨ b` then `a ∨ b = a`.
- **`LinearLattice`**: Class extending `LinearOrder` and `BiorderedLattice`, deriving the strict universal properties `<_meet-univ` and `<_join-univ` from comparison.

#### Min/Max on Arrays

- **`LinearOrder.findMin`**: For a non-empty array `l : Array A (suc n)` over a `Dec` linear order, returns an index `j` such that `Big_∧ l = l j` (the position of the minimum).
- **`LinearOrder.findMax`**: For a non-empty array, returns the index of the maximum, with `Big_∨ l = l j`.

#### Monotone Functions

- **`monotone-injective`**: A strictly monotone function `f : X -> Y` from a `LinearOrder` to a `StrictPoset` is injective.

#### Dense and Unbounded Orders

- **`DenseLinearOrder`**: Class extending `LinearOrder` with `isDense`: between any `x < z` there exists `y` with `x < y < z`.
- **`DenseLinearOrder.Dec`**: Class extending `DenseLinearOrder` and `LinearOrder.Dec` — decidable dense linear orders.
- **`UnboundedDenseLinearOrder`**: Class extending `DenseLinearOrder` with `withoutUpperBound` and `withoutLowerBound`, asserting the absence of maximal and minimal elements.
- **`UnboundedDenseLinearOrder.Dec`**: Class extending `UnboundedDenseLinearOrder` and `DenseLinearOrder.Dec` — decidable unbounded dense linear orders (e.g. the rationals).

#### Trichotomy

- **`Tri`**: Three-way comparison data type over a `StrictPoset`, with constructors `less (a < a')`, `equals (a = a')`, `greater (a > a')`. Proven to be a proposition via `levelProp`.
