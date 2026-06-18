### Algebra.Ring.Localization.Properties

Properties and uniqueness results for ring localizations.

This module establishes that localization of a commutative ring at a subset is unique up to canonical isomorphism, and characterizes its categorical behavior. The central technique is the universal property: given two localizations, each lifts the inclusion of the other to produce a mutually inverse pair, yielding an isomorphism. The module also relates localization at a subset to localization at its multiplicative closure, and shows that the localization map is a categorical epimorphism in `CRing`.

#### Uniqueness

- **`localization-unique`**: Any two localizations `l1 l2 : Localization R S` of a commutative ring `R` at a subset `S` are equal. Built by transporting along the canonical isomorphism produced by `lift-iso` between the two localizations.
- **`localization-unique.lift-iso`**: Constructs an `Iso` between two localizations `l1` and `l2` (possibly at different subsets `S1`, `S2`) given that elements of each subset are inverted in the other localization. The isomorphism is the universal-property lift `l1.lift l2.inL p2`.

#### Closure Compatibility

- **`localization-closure-equiv`**: A localization at `S` is canonically isomorphic to a localization at the multiplicative `closure S`. The map is the universal lift of `l2.inL` along the proof that elements of `S` (and hence of its closure) become invertible.
- **`localization-closure-equiv.inv-closure`**: Auxiliary lemma showing that every element in the multiplicative closure of `S` is mapped to a `Monoid.Inv` element in any localization at `S`. Provides the invertibility data needed to apply the universal property.

#### Categorical Properties

- **`locMap-epi`**: The canonical localization map `locMap : R -> Loc R S` is an epimorphism in the category of commutative rings, for any `SubMonoid S` of `R`. Reflects the fact that ring homomorphisms out of a localization are determined by their values on the image of `R`.
