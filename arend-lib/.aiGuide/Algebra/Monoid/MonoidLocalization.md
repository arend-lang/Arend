### Algebra.Monoid.MonoidLocalization

Constructs the localization of a (commutative or abelian) monoid at a submonoid, yielding a quotient of pairs `(numerator, denominator)`, and uses it to build the Grothendieck group of an abelian monoid.

#### Multiplicative Localization

- **`LocType`**: Localization of a commutative monoid `M` at a submonoid `S`, defined as the quotient of pairs `\Sigma (x y : M) (S y)` by the relation `a ~ b` iff `a.1 * b.2 = b.1 * a.2`.
- **`LocType.SType`**: The underlying type of pairs `(x, y, p : S y)` representing fractions `x/y`.
- **`LocType.~`**: Equivalence relation on `SType` capturing fraction equality: `a.1 * b.2 = b.1 * a.2`.
- **`LocType.inl~`**: Canonical map from `SType S` into `LocType S`.
- **`LocType.~-lequiv`**: Lifts the relation `a ~ b` to an equality `inl~ a = inl~ b` in the quotient.
- **`LocType.~-lequiv-right`**: Variant of `~-lequiv` allowing cancellation by an extra element `c ∈ S`: from `a.1 * b.2 * c = b.1 * a.2 * c` deduces `inl~ a = inl~ b`.
- **`LocMonoid`**: Instance making `LocType S` a `CMonoid` with unit `1/1`, multiplication of fractions, and the standard commutative-monoid laws proved via the equation tactic.

#### Additive Localization

- **`LocAbType`**: Additive analogue of `LocType` for an abelian monoid `M` and an additive submonoid `S`, with relation `a.1 + b.2 = b.1 + a.2`.
- **`LocAbType.SType`**: Underlying pair type `\Sigma (x y : M) (S y)`.
- **`LocAbType.~`**: Additive fraction-equivalence relation.
- **`LocAbType.inl~`**: Canonical inclusion of `SType S` into `LocAbType S`.
- **`LocAbType.~-lequiv`**: Lifts `a ~ b` to equality in the quotient.
- **`LocAbType.~-lequiv-right`**: Cancellation-by-`c` variant for the additive setting.
- **`LocAbMonoid`**: Instance making `LocAbType S` an `AbMonoid` with zero `0/0`, addition of fractions, and the abelian-monoid laws.

#### Grothendieck Group

- **`MaxLocAbType`**: Localization of an abelian monoid `M` at the maximal submonoid (containing every element), i.e. all pairs of elements are admitted as fractions.
- **`GrothendieckAbGroup`**: Instance exhibiting `MaxLocAbType M` as an `AbGroup`, extending `LocAbMonoid SubAddMonoid.max` with negation defined componentwise; this is the standard Grothendieck group construction turning an abelian monoid into an abelian group.
