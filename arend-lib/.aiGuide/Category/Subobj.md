### Category.Subobj

Subobjects and regular subobjects of an object in a precategory, organized as preorders under factorization.

A subobject of `obj` is a monomorphism into `obj`; a regular subobject is a regular mono into `obj`. The preorder `s <= t` holds when `s` factors through `t`, and antisymmetry of this preorder yields an isomorphism between the underlying domains. The module also connects regular subobjects to limits: meets of regular subobjects correspond to pullbacks, giving a translation between order-theoretic and limit-theoretic descriptions of intersections.

#### Subobjects

- **`Subobj`**: Data type of subobjects of `obj : C`, with constructor `subobj` packaging a domain `sub` and a monomorphism `sub -> obj`.
- **`SubobjPreorder`**: Preorder on `Subobj obj` where `f <= g` iff there exists `h` with `g ∘ h = f`. Reflexivity uses identity; transitivity composes the witnesses.

#### Subobject Preorder Lemmas

- **`SubobjPreorder.extractMap`**: From a propositionally-truncated factorization through a mono `g`, extract the actual factoring map and equation. Uses the uniqueness afforded by `Mono` to lift `∃` to `\Sigma`.
- **`SubobjPreorder.antisymmetric`**: Two mutually-dominating subobjects have isomorphic domains, producing an `Iso` whose components are extracted from the existence proofs and whose inverse laws follow from monicity.

#### Regular Subobjects

- **`RegularSubobj`**: Data type of regular subobjects of `obj`, with constructor `regSubobj` packaging a regular mono `f : sub -> obj`.
- **`RegularSubobjPreorder`**: Preorder on `RegularSubobj obj` defined analogously to `SubobjPreorder` via factorization.

#### Pullbacks and Meets of Regular Subobjects

- **`pullback_regularSubobj-isMeet`**: Given a pullback `P` of two regular monos `P.f`, `P.g`, the regular subobject `regSubobj (P.f ∘ pbProj1) ...` is the meet of `regSubobj P.f fm` and `regSubobj P.g gm` in the regular-subobject preorder. Establishes that pullbacks compute intersections of regular subobjects.
- **`pullback_regularSubobj-isMeet.pullback-isRegularMono`**: The composite `P.f ∘ pbProj1` is itself a regular mono when `P.f` and `P.g` are, so the meet is again a regular subobject.
- **`regularSubobj_meet-pullback`**: Converse direction in a finitely complete precategory: any meet of two regular subobjects, witnessed by a third regular mono `h`, exhibits the domain `w` of `h` as a pullback of `f` and `g`. Built by transporting the canonical pullback along the iso from `antisymmetric`.
- **`regularSubobj_meet-pullback.exact`**: Refined version that produces a pullback square `Pullback f g w p1 p2` with prescribed projections `p1, p2` (given a coherence `f ∘ p1 = g ∘ p2`), by showing the canonical pullback's projections coincide with `p1, p2` via monicity.
