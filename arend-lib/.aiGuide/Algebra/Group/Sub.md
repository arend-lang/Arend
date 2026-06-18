### Algebra.Group.Sub

Subgroups, normal subgroups, and quotient groups.

This module formalizes subgroups by extending `SubMonoid` with closure under inversion, yielding an induced group structure `IGroup` on the underlying sub-set. The equivalence `x ~ y ⟺ x⁻¹·y ∈ H` produces left cosets as a quotient set, and when the subgroup is normal (closed under conjugation), this quotient inherits a well-defined group structure via `NormalSubGroup.quotient`. Variants are provided for additive groups (`SubAddGroup`), commutative groups (`cStruct`/`abStruct`), decidable membership (`DecSubGroup`), and finite groups (`FinSubGroup`), and an alternative single-axiom presentation (`anotherSubgroupDefinition`) shows that closure under `x⁻¹·y` together with `1 ∈ X` suffices.

#### Subgroup Class

- **`SubGroup`**: Extends `SubMonoid` with `contains_inverse`. Carries an underlying `Group` `S` and asserts the membership predicate is closed under inversion.
- **`SubGroup.IGroup`**: The induced group structure on the sub-set, with inversion lifted componentwise.
- **`SubGroup.equivalence`**: The coset equivalence `x ~ y := x⁻¹·y ∈ H` on `S`.
- **`SubGroup.Cosets`**: The quotient set `S/~` of left cosets.
- **`SubGroup.contains->equiv`**: If `x ∈ H` then `1 ~ x`.
- **`SubGroup.equiv->contains`**: If `1 ~ x` then `x ∈ H` (the converse).
- **`SubGroup.invariant-right-multiplication`**: If `1 ~ y`, then `[x] = [x·y]` in the coset quotient.
- **`SubGroup.equivalence-to-1`**: `x ~ y` implies `1 ~ x⁻¹·y`, normalizing the equivalence to a relation against the identity.
- **`SubGroup.cStruct`**: Promotes a `SubGroup` of a commutative group `G` to a `CGroup` structure on the induced sub-group.

#### Decidable and Alternative Definitions

- **`DecSubGroup`**: Subgroup with decidable membership; extends `SubGroup` and `DecSubMonoid`.
- **`anotherSubgroupDefinition`**: Builds a `SubGroup` from a sub-set `X` together with `1 ∈ X` and the single closure axiom `x, y ∈ X ⟹ x⁻¹·y ∈ X`. Useful when verifying the standard one-axiom criterion.

#### Order on Subgroups

- **`SubGroupPreorder`**: `Preorder` instance on `SubGroup G` defined by pointwise inclusion of membership predicates.

#### Normal Subgroups and Quotient

- **`NormalSubGroup`**: Extends `SubGroup` with `isNormal`: closure under conjugation `g·h·g⁻¹` for any `g ∈ S` and `h ∈ H`.
- **`NormalSubGroup.isNormal'`**: Equivalent normality form using `g⁻¹·h·g`.
- **`NormalSubGroup.quotient`**: The quotient group `S/N` on `Cosets`, with multiplication and inversion descending to the quotient via the normality condition.
- **`NormalSubGroup.quotient-proj-setwise`**: The canonical projection `S → S/N` as a `SetHom`.
- **`NormalSubGroup.criterionForKernel`**: Elements of `N` map to the identity of the quotient: `[a] = 1` whenever `a ∈ N`.
- **`NormalSubGroup.quotIsSurj`**: Surjectivity of the quotient projection.
- **`kernelEquivProp`**: Top-level version of the kernel criterion: for `N : NormalSubGroup G` and `a ∈ N`, `quotient.ide = in~ a`.

#### Additive Subgroups

- **`SubAddGroup`**: Additive analogue of `SubGroup`; extends `SubAddMonoid` with `contains_negative`.
- **`SubAddGroup.contains_-`**: Closure under subtraction `x - y`.
- **`SubAddGroup.IAddGroup`**: Induced `AddGroup` structure on the sub-set.
- **`SubAddGroup.abStruct`**: Promotes a `SubAddGroup` of an `AbGroup` to an `AbGroup`.
- **`SubAddGroup.max`**: The maximal sub-additive-group containing every element of `A`.

#### Finite Subgroups

- **`FinSubGroup`**: Extends `DecSubGroup` with an underlying `FinGroup`.
- **`FinSubGroup.IFinGroup`**: The induced finite group on the decidable sub-set, using `SigmaFin.DecSubSet-isFin` to obtain finiteness of the carrier.
