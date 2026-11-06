### Algebra.Group.SubGroup

Subgroups of groups and additive groups, normal subgroups with their quotients, and kernels and images of homomorphisms.

#### Subgroups

- **`SubGroup`**: Extends `SubMonoid` with `contains_inverse`. Carries an underlying `Group` `S` and asserts the membership predicate is closed under inversion.
- **`SubGroup.equivalence`**: The coset equivalence `x ~ y := x⁻¹·y ∈ H` on `S`.
- **`SubGroup.Cosets`**: The quotient set `S/~` of left cosets.
- **`SubGroup.inc~`**: The canonical projection of an element of `S` to its coset in `Cosets`.
- **`SubGroup.contains_~`**: An element `x` lies in the subgroup iff `1 ~ x`.
- **`SubGroup.~-pequiv-left`**: If `1 ~ y`, then `x` and `x * y` have the same coset: `inc~ x = inc~ (x * y)`.
- **`DecSubGroup`**: Subgroup with decidable membership; extends `SubGroup` and `DecSubMonoid`.
- **`FinSubGroup`**: Extends `DecSubGroup` with an underlying `FinGroup`.
- **`SubGroupPoset`**: The poset of subgroups of a group `G`, ordered by inclusion.
- **`IGroup`**: The group structure on the elements of a subgroup `S` (the subgroup viewed as a group); `IGroup.embed` is the inclusion homomorphism into the ambient group.
- **`ICGroup`**: The subgroup of a commutative group is again a commutative group.
- **`IFinGroup`**: The group structure of a `FinSubGroup` is a `FinGroup`.

#### Normal subgroups and quotients

- **`NormalSubGroup`**: Extends `SubGroup` with `isNormal`: closure under conjugation `g·h·g⁻¹` for any `g ∈ S` and `h ∈ H`.
- **`NormalSubGroup.isNormal'`**: Equivalent normality form using `g⁻¹·h·g`.
- **`NormalSubGroup.Quot`**: The quotient group `S/H` with underlying set `Cosets`.
- **`NormalSubGroup.quotHom`**: The canonical projection homomorphism `S -> Quot`.
- **`NormalSubGroup.quotHom-surj`**: `quotHom` is surjective.
- **`NormalSubGroup.quotHom_=1`**: `quotHom` sends every element of the subgroup to the unit of `Quot`.
- **`NormalSubGroup.quotHom-eq`**: If `x * inverse y` lies in the subgroup, then `x` and `y` have the same image under `quotHom`.
- **`NormalSubGroup.quot-univ`**: Universal property of the quotient: a homomorphism `f : S -> K` that sends the subgroup to `1` factors through `Quot`.
- **`NormalSubGroup.quot-univ-beta`**: The factorization `quot-univ f p` composed with `quotHom` is `f`.
- **`NormalSubGroup.quot-hom-ext`**: Two homomorphisms out of `Quot` that agree on all `quotHom x` are equal.

#### Additive groups

- **`SubAddGroup`**: Additive analogue of `SubGroup`; extends `SubAddMonoid` with `contains_negative`.
- **`SubAddGroup.contains_-`**: Closure under subtraction `x - y`.
- **`SubAddGroup.contains_*i`**: Closure under integer multiples `n *i x`.
- **`SubAddGroup.max`**: The maximal sub-additive-group containing every element of `A`.
- **`IAddGroup`**: The additive group structure on the elements of a `SubAddGroup`.
- **`IAbGroup`**: A sub-additive-group of an abelian group is again an abelian group.

#### Kernels, preimages and images

- **`KernelGroup`**: The kernel of a group homomorphism `f`, as a normal subgroup of `f.Dom`.
- **`Kernel-trivial-char`**: A group homomorphism has trivial kernel iff it is injective.
- **`KernelAddGroup`**: The kernel of an additive group homomorphism, as a `SubAddGroup` of `f.Dom`.
- **`AddKernel-trivial-char`**: An additive group homomorphism has trivial kernel iff it is injective.
- **`PreimageGroup`**: The preimage `f⁻¹(H)` of a subgroup `H` of `f.Cod` under a homomorphism `f`, as a subgroup of `f.Dom`.
- **`ImageGroup`**: The image of a group homomorphism `f` as a subgroup of `f.Cod`.
- **`ImGroup`**: The image of `f` viewed as a group.
- **`ImGroupLeftHom`**: The corestriction of `f` to a homomorphism `f.Dom -> ImGroup f`.
- **`ImageAddGroup`**: The image of an additive group homomorphism `f` as a `SubAddGroup` of `f.Cod`.
- **`ImAddGroup`**: The image of `f` viewed as an additive group.
- **`ImAddGroupLeftHom`**: The corestriction of `f` to an additive homomorphism `f.Dom -> ImAddGroup f`.
