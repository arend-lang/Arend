### Algebra.Ring.Localization.Properties

Properties of ring localizations: uniqueness up to isomorphism, behavior under subset closure, and the epi property of the localization map.

#### Uniqueness

- **`localization-unique`**: Any two localizations of a commutative ring `R` at a sub-set `S` are equal: `l1 = l2`. Constructed by lifting between the two localizations to obtain an iso, then using `Cat.isotoid` to convert the iso into a path.
- **`localization-unique.lift-iso`**: Given localizations `l1` at `S1` and `l2` at `S2` such that `S2`-elements become invertible in `l1` and `S1`-elements become invertible in `l2`, the universal map `l1.lift l2.inL p2` is an isomorphism. This is the core comparison lemma underlying uniqueness.

#### Closure Invariance

- **`localization-closure-equiv`**: Localizing at `S` is equivalent to localizing at its monoid closure `closure S`. Specifically, the lift `l1.lift l2.inL` from a localization at `S` to a localization at `closure S` is an iso, witnessing that closing `S` under multiplication and `1` does not change the localization.
- **`localization-closure-equiv.inv-closure`**: In any localization `l` at `S`, every element of `closure S` becomes invertible: `closure S x -> Monoid.Inv (l.inL x)`. Justifies why lifting along the inclusion `S -> closure S` is well-defined.

#### Epimorphism Property

- **`locMap-epi`**: The canonical localization map `locMap : R -> R[S^{-1}]` is an epimorphism in the category of commutative rings. Two ring homomorphisms out of the localization that agree after precomposing with `locMap` must be equal.
