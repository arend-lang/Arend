### Category.Subobj

Subobjects and regular subobjects of an object in a category, ordered by factorization through monomorphisms.

#### Subobjects

- **`Subobj`**: Data type of subobjects of `obj : C`, given by a domain `sub` together with a monomorphism `Mono {C} {sub} {obj}`.
- **`subobj`**: Constructor packaging a domain and a mono into a `Subobj`.
- **`SubobjPreorder`**: `Preorder` instance on `Subobj obj` where `f ≤ g` iff there exists `h` with `g ∘ h = f` (factorization through the larger mono).
- **`SubobjPreorder.extractMap`**: Given a mono `g` and the propositional truncation `∃ h, g ∘ h = f`, recovers an actual `(h, g ∘ h = f)` (uniqueness of the factorization through a mono lets us project out of `∃`).
- **`SubobjPreorder.antisymmetric`**: Two mutually-dominating subobjects (mono `f` and mono `g` with factorizations each way) are isomorphic via an `Iso` between their domains.

#### Regular Subobjects

- **`RegularSubobj`**: Data type of regular subobjects of `obj : C`, given by a domain and a regular monomorphism `f : Hom sub obj` together with `isRegularMono f`.
- **`regSubobj`**: Constructor packaging a regular mono into a `RegularSubobj`.
- **`RegularSubobjPreorder`**: `Preorder` instance on `RegularSubobj obj` defined analogously to `SubobjPreorder` via factorizations.

#### Pullbacks and Meets of Regular Subobjects

- **`pullback_regularSubobj-isMeet`**: In a `CartesianPrecat`, the pullback `P` of two regular monos `P.f`, `P.g` exhibits `regSubobj (P.f ∘ pbProj1)` as the meet of `regSubobj P.f` and `regSubobj P.g` in the regular subobject preorder.
- **`pullback_regularSubobj-isMeet.pullback-isRegularMono`**: The composite `P.f ∘ pbProj1` is itself a regular mono whenever `P.f` and `P.g` are (closure of regular monos under pullback).
- **`regularSubobj_meet-pullback`**: Converse direction — in a `FinCompletePrecat`, any regular mono `h` realizing the meet of `regSubobj f` and `regSubobj g` arises as a pullback of `f` along `g`, by transporting along the iso between `h`'s domain and the canonical pullback.
- **`regularSubobj_meet-pullback.exact`**: Sharper form producing a `Pullback f g w p1 p2` with prescribed projections `p1`, `p2` (given a coherence square `f ∘ p1 = g ∘ p2`), by showing the canonical pullback projections agree with `p1`, `p2` via mono-cancellation.
