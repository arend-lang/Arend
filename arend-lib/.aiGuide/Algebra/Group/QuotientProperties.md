### Algebra.Group.QuotientProperties

Universal property of group quotients and the First Isomorphism Theorem.

This module formalizes the standard package of results connecting normal subgroups, quotient groups, and homomorphisms. The central abstraction `UniversalGroupQuotient` packages a homomorphism `f : G → H` together with a normal subgroup `N ⊆ ker f`, and constructs the induced morphism `G/N → H` factoring `f` through the quotient. This is then specialized to `FirstIsomorphismTheorem`, where taking `N = ker f` and assuming surjectivity yields an isomorphism `G/ker f ≅ H`. Auxiliary classes like `GroupTriangle` capture the commutative-triangle data used in the 2-out-of-3 surjectivity argument, and the corollaries transport the result along an equality `N = ker f` for cases where the kernel is presented indirectly.

#### Notation

- **`//`**: Infix notation for the quotient group `G // H = H.quotient`, where `H : NormalSubGroup G`.

#### Commutative Triangles

- **`GroupTriangle`**: Class bundling three groups `X, Y, Z` and homomorphisms `f : X → Y`, `g : Y → Z`, `h : X → Z` with a commutativity witness `h = g ∘ f`.
  - **`surjectivity-2-out-3`**: If `h` and `f` are surjective, so is `g`.
  - **`surjectivity-2-out-3-pw`**: Pointwise version: every `z : Z` has a preimage under `g`.

#### Universal Property of Group Quotients

- **`UniversalGroupQuotient`**: Class capturing the universal property data: a homomorphism `f : G → H` and a normal subgroup `N ⊆ ker f`. Provides the factorization of `f` through `G/N`.
  - **`universalQuotientMorphismSetwise`**: The set-level map `G/N → H` defined on representatives by `[g] ↦ f g`, with the well-definedness proof using `N ⊆ ker f`.
  - **`uqms`**: Short alias for `universalQuotientMorphismSetwise`.
  - **`universalQuotientMorphismMultiplicative`**: Shows `uqms` preserves multiplication.
  - **`universalQuotientMorph`**: The induced group homomorphism `G // N → H`.
  - **`universalQuotientProperty`**: Commutativity equation `universalQuotientMorph ∘ quotient-map = f`.

#### First Isomorphism Theorem

- **`FirstIsomorphismTheorem`**: Class wrapping a homomorphism `f : G → H` and providing the canonical isomorphism `G/ker f ≅ im f` (an isomorphism onto `H` when `f` is surjective).
  - **`UniversalProperties`**: Specialization of `UniversalGroupQuotient` to `N = ker f`.
  - **`Triangle`**: The commutative triangle `f = univ ∘ quot` packaged as a `GroupTriangle`.
  - **`univ`**: The induced homomorphism `G/ker f → H`.
  - **`quot`**: The canonical quotient projection `G → G/ker f`.
  - **`universalKerProp`**: Commutativity `univ ∘ quot = f`.
  - **`universalQuotientKernel`**: If `univ g = ide` then `g` is the identity coset; established via the representative-level helper `helper-1`.
  - **`evidTrivKer`**: `univ` has trivial kernel.
  - **`universalQuotientKernel'`**: Element-level kernel lemma: if `univ [a] = ide` then `a ∈ ker f`, using `technical-helper` showing `univ [a] = f a`.
  - **`univKer-mono`**: `univ` is injective.
  - **`univKer-epi`**: If `f` is surjective, so is `univ` (via the 2-out-of-3 lemma).
  - **`FirstIsoTheorem`**: For surjective `f`, `univ` is a group isomorphism.

#### Corollaries

- **`GroupFirstIsoCorollary-setwise`**: For any `N` with `N = ker f` and `f` surjective, produces a homomorphism `G/N → H` together with a proof it is an isomorphism, by transporting along `inv p`.
  - **`GroupFirstIsoCorollary-2`**: Bare construction of `univ : G/ker f → H` from `f`.
  - **`GroupFirstIsoCorollary-24`**: Pairs `GroupFirstIsoCorollary-2` with the isomorphism proof under surjectivity.
  - **`GroupFirstIsoCorollary-3`**: Transports `univ` along `N = ker f` to get a homomorphism `G/N → H` (without the isomorphism witness).
- **`GroupFirstIsoCorollary`**: Categorical version returning a homomorphism `G/N → H` together with an `Iso` in `GroupCat`, by post-composing with `GroupCat.Iso<->Inj+Surj`.
