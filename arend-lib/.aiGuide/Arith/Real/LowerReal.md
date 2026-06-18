### Arith.Real.LowerReal

Dedekind-style lower reals as downward-closed, rounded, open subsets of the rationals.

A `LowerReal` is represented by its set of strict lower bounds `L : Rat -> \Prop`, required to be downward-closed under `<` and rounded (every element has a strictly larger element still in `L`). Rationals embed via `q' < q`, and the standard ordered abelian-monoid structure (addition, `<=`, `<`, meet, join) is built directly on these predicates. The `ExtendedLowerReal` variant drops the inhabitedness condition `L-inh`, which lets it represent `-∞` and form a complete lattice closed under arbitrary joins; `LowerReal` itself is closed only under bounded joins (joins together with a fixed lower-real lower bound), captured by `BJoin`.

#### Core Records

- **`ExtendedLowerReal`**: A predicate `L : Rat -> \Prop` on rationals that is downward-closed (`L-closed`) and rounded/open (`L-rounded`); represents a lower real possibly equal to `-∞`.
- **`ExtendedLowerReal.fromRat`**: Coercion `Rat -> ExtendedLowerReal` sending `x` to the predicate `< x`.
- **`ExtendedLowerReal.L_<=`**: Downward closure extended from `<` to `<=`: if `L q` and `r <= q` then `L r`.
- **`LowerReal`**: Extends `ExtendedLowerReal` with `L-inh`, asserting `L` is inhabited; rules out `-∞`.
- **`LowerReal.fromRat`**: Coercion `Rat -> LowerReal`.

#### Bounded Joins

- **`LowerReal.BJoin`**: Bounded supremum of a family `f : J -> ExtendedLowerReal` together with a `LowerReal` lower bound `b`; defined by `b.L q || ∃ j, (f j).L q`. The bound `b` ensures the result is a genuine `LowerReal` even if `J` is empty.
- **`LowerReal.BJoin-bound`**: `b <= BJoin b f`.
- **`LowerReal.BJoin-cond`**: Each `f j <= BJoin b f`.
- **`LowerReal.BJoin-univ`**: Universal property: any `e` above `b` and all `f j` is above `BJoin b f`.

#### Ordered Abelian Monoid Structure

- **`LowerRealAbMonoid`**: Instance of `BiorderedLatticeAbMonoid` on `LowerReal`, packaging zero, addition, `<`, `<=`, meet, and join with all the order/algebra compatibility laws.
- **`LowerRealAbMonoid.+`**: Addition of lower reals: `L (x+y) a` iff `∃ b ∈ x.L, c ∈ y.L, a < b + c`.
- **`LowerRealAbMonoid.+_L`**: Characterization of membership in `(x + y).L`.
- **`LowerRealAbMonoid.+-rat`**: Embedding `fromRat` is a homomorphism: `fromRat x + fromRat y = fromRat (x + y)`.
- **`LowerRealAbMonoid.<=`**: Order on `ExtendedLowerReal`: `x <= y` iff `x.L ⊆ y.L`.
- **`LowerRealAbMonoid.<`**: Strict order: `x < y` iff some rational `b ∈ y.L` already dominates `x` (i.e. `x <= b`).
- **`LowerRealAbMonoid.lower_<_L`**: For rational `a`, `(a : LowerReal) < x` iff `x.L a`; ties the strict order back to membership.
- **`LowerRealAbMonoid.meet`**: Pointwise intersection: `L (meet x y) a = (x.L a × y.L a)`.
- **`LowerRealAbMonoid.meet_L`**: Membership characterization for `meet`.
- **`LowerRealAbMonoid.join`**: Pointwise union: `L (join x y) a = x.L a || y.L a`.
- **`LowerRealAbMonoid.join_L`**: Membership characterization for `join`.
- **`LowerRealAbMonoid.zro<ide`**: `0 < 1` as lower reals.
- **`LowerRealAbMonoid.<=-char`**: If `¬ x.L a` then `x <= fromRat a`; the standard way to upper-bound a lower real by a rational.
- **`LowerRealAbMonoid.<_+`**: Strict monotonicity of addition: `a < c` and `b < d` imply `a + b < c + d`.

#### Complete Lattice on Extended Lower Reals

- **`ExtendedLowerRealLattice`**: `CompleteLattice` instance on `ExtendedLowerReal` with arbitrary `Join` given by union of the predicates `L`. Inhabitedness is not required, so the empty join (representing `-∞`) is well-defined; this is the natural ambient lattice in which `BJoin` lives once a lower bound is supplied.
