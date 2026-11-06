### AG.AffineScheme

Affine schemes: the spectrum `Spec R` of a commutative ring as a scheme, and the characterization of affine schemes among locally ringed locales via the unit `specUnit` of the adjunction.

- **`Spec`**: The spectrum of a commutative ring `R` as a `Scheme`, defined as `(satSite R).toScheme` from the saturated scheme site `Spec.satSite R` on the trivial poset with `R` as the ring on every element.
- **`specUnit`**: The unit of the adjunction between affine schemes and locally ringed locales: for a locally ringed locale `X`, a morphism `X -> Spec X.GlobalSections` (built from `specUnit.adjoint` applied to the identity of the ring of global sections).
- **`IsAffineScheme-char`**: A locally ringed locale `X` is an affine scheme (`X.IsAffineScheme`) iff `specUnit` is an isomorphism (`IsIso`).
