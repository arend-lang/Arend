### Homotopy.Truncation

Defines truncated types of bounded h-level and the higher inductive truncation operator that forces a type to a given h-level.

#### Truncated Types as a Class

- **`Truncated_-1+`**: A class bundling a type `A` with a proof that it has h-level `n` (i.e., `A ofHLevel_-1+ n`).
- **`Truncated_-1+.ext`**: Equality of truncated types reduces to equality of their underlying types — `t.A = t'.A` lifts to `t = t'`.
- **`Truncated_-1+.equiv`**: The canonical equivalence `(t = t') ≃ (t.A = t'.A)`, exhibiting the underlying type projection as an equivalence on identities.
- **`Truncated_-1+.up`**: Promotes a truncated type of level `n` to level `suc n` via `HLevel_-1_suc`.

#### H-Level of Truncated Structures

- **`truncatedEquiv`**: Instance showing the type of equivalences `Equiv {t} {t'}` between two `n`-truncated types is itself `n`-truncated, via embedding into the function space and `HLevels-pi`.
- **`truncatedTypesLevel`**: Instance: the universe `Truncated_-1+ n` of `n`-truncated types is `(suc n)`-truncated, using univalence and the equivalence between identities and underlying type identities.

#### The Truncation Higher Inductive Type

- **`Trunc_-1+`**: Higher inductive type freely truncating `A` to h-level `n`. Constructors:
  - **`inT`**: Inclusion `A -> Trunc_-1+ n A`.
  - **`hubT`**: Hub point for any `Sphere n`-shaped diagram in the truncation.
  - **`spokeT`**: Spoke filling each point of the sphere diagram to the hub, ensuring all `n`-spheres are nullhomotopic.

#### Properties of `Trunc_-1+`

- **`Trunc_-1+.level`**: Instance: `Trunc_-1+ n A` has h-level `n`, proved via `loop-level-iter` and the sphere–loop equivalence `SphereLoopEquiv`, exhibiting pointed maps from the sphere as contractible.
- **`Trunc_-1+.elim`**: Dependent eliminator into a family of `n`-truncated types: given `g : \Pi (a : A) -> P (inT a)`, extends to `\Pi (x : Trunc_-1+ n A) -> P x`, handling `hubT`/`spokeT` automatically using truncatedness of `P`.
- **`Trunc_-1+.elim2`**: Two-argument version of `elim` for binary families.
- **`Trunc_-1+.equality`**: Characterization of identity types: `(inT a = inT a') ≃ Trunc_-1+ n (a = a')` in `Trunc_-1+ (suc n) A`, via encode–decode using `elim2`. This is the key lemma showing truncation lowers h-level on path spaces by one.
