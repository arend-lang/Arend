### Algebra.Module

Defines left modules over a ring and develops the linear-algebra machinery of independence, generation, and bases.

This module introduces `LModule R` as an abelian group equipped with a scalar multiplication `*c : R -> E -> E` satisfying the usual axioms. The bulk of the file develops two parallel formulations of linear (in)dependence and generation: an indexed array-based version (`IsIndependent`, `IsGenerated`, `IsBasis`) suited to finite presentations, and a set-based version (`IsIndependentSet`, `IsGeneratingSet`, `IsBasisSet`) parameterized by an arbitrary indexing set, with equivalences between the two formulations. The set-based independence uses an auxiliary equivalence relation `Z~` on formal sums (arrays of `(R, J)` pairs) modulo zero-sums, capturing when two formal linear combinations represent the same element. Standard module constructions are also provided: products, the regular module `R` over itself, function modules `Array M n`, and pullback/composition of modules along ring homomorphisms.

#### Main Class

- **`LModule`**: Left module over a `Ring R`, extending `AbGroup`. Provides scalar multiplication `*c : R -> E -> E` with associativity (`*c-assoc`), left and right distributivity (`*c-ldistr`, `*c-rdistr`), and unit law (`ide_*c`).

#### Basic Module Lemmas

- **`cancel`**: Cancellation by an invertible scalar: if `r` is invertible and `r *c a = r *c b`, then `a = b`.
- **`*c_zro-left`**, **`*c_zro-right`**: Scalar/vector zero annihilates: `0 *c a = 0` and `r *c 0 = 0`.
- **`*c_negative-left`**, **`*c_negative-right`**: Negation commutes with scalar multiplication on either side.
- **`*c-ldistr_-`**, **`*c-rdistr_-`**: Distributivity over subtraction on either side.
- **`neg_ide_*c`**: `-1 *c a = negative a`.
- **`natCoef_*c`**: `natCoef n *c a = n *n a` (compatibility with the natural-number coefficient action).
- **`*c_BigSum-rdistr`**, **`*c_BigSum-ldistr`**: Distributivity of `*c` over `BigSum` on either argument.
- **`*c_FinSum-rdistr`**, **`*c_FinSum-ldistr`**: Same as above for `FinSum` indexed by a `FinSet`.

#### Linear Independence and Generation (Array-Based)

- **`IsDependent`**: A list `l` admits a nontrivial linear combination summing to zero.
- **`IsIndependent`**: Every linear combination summing to zero has all coefficients zero.
- **`IsGenerated`**: Every element is a linear combination of `l`.
- **`IsBasis`**: Conjunction of `IsIndependent` and `IsGenerated`.
- **`independent-subset`**: A prefix of an independent list is independent.
- **`independent-nonZero`**: If a basis element equals zero, then `0 = 1` in `R` (so `R` is trivial).

#### Linear Independence and Generation (Set-Based)

- **`IsIndependentSet`**: For `g : J -> E`, every formal sum (an `Array (\Sigma R J)`) summing to zero is a `IsZeroSum`. Has a `\where`-block of helpers for working with formal sums.
  - **`IsIndependentSet.sum`**: Evaluate a formal sum `Array (\Sigma R J)` against `g`.
  - **`sum-ldistr`**, **`sum_++`**, **`sum_Big++`**, **`sum_negative`**, **`sum_EPerm`**: Algebraic properties of `sum` (scalar distribution, concatenation, flattening, negation, permutation invariance).
  - **`=_~`**: Independence implies that equal sums are `Z~`-related.
  - **`IsZeroSum_=`**, **`~_=`**: Zero-sums evaluate to `0`; `Z~`-related sums evaluate equally.
- **`IsIndependentSet-right-inj`**: Pulling back an independent set along an injection stays independent.
- **`IsIndependentDec`**: Variant of independence that quantifies over injective `Array J` indexings.
- **`IsIndependentSet<->IsIndependentDec`**: Equivalence of `IsIndependentSet` and `IsIndependentDec` for decidable `J`.
- **`IsIndependent<->IsIndependentSet`**: Array independence agrees with set independence (over `Fin l.len`).
- **`IsIndependentSet-fin`**: For finite `J`, set independence reduces to coefficient-wise vanishing of `FinSum`.
- **`IsGeneratingSet`**: Every element is in the image of `sum g` for some formal sum.
- **`IsGeneratingSet-fin`**: Equivalent characterization in terms of `FinSum` over arbitrary finite index sets.
- **`IsGeneratingSet-surj`**: Pushing forward a generating set along a surjection stays generating.
- **`IsBasisSet`**: Conjunction of `IsIndependentSet` and `IsGeneratingSet`.
- **`IsBasisSet-equiv`**: Bases transport along equivalences of index sets.
- **`IsGenerated<->IsGeneratingSet`**, **`IsBasis<->IsBasisSet`**: Equivalences with the array-based notions.

#### Basis Decomposition

- **`independent-split-unique`**: Two combinations equal as sums must agree coefficient-wise on an independent list.
- **`basis-split-pair`**: For a basis `l` and element `x`, returns the unique coefficient array witnessing `x = BigSum (c j *c l j)`, packaged as a propositionally unique pair.
  - **`basis-split-unique`** (inner): The decomposition is propositionally unique.
- **`basis-split`**: The coefficient array projecting `x` onto the basis `l`.
- **`basis-split-char`**: `x = BigSum (basis-split lb x j *c l j)`.
- **`basis-split-unique`** (outer): Any decomposition coincides with `basis-split`.
- **`basis_split_basis`**, **`basis_split_=`**, **`basis_split_/=`**: `basis-split` of a basis vector is the Kronecker delta.

#### Finite Generation

- **`IsGeneratedFin`**: Every element is a `FinSum` over a `FinSet`.
- **`IsFinitelyGenerated`**: Existence of a finite generating array.
- **`basisSet_basis`**: From a basis indexed by a `FinSet`, extract a basis array of length `J.finCard`.
- **`free-char`**: Free-module characterization: array bases ↔ basis sets indexed by some `FinSet`.
- **`generated-array-fin`**, **`generated-fin-array`**: Conversions between `IsGenerated` (array) and `IsGeneratedFin` / `IsFinitelyGenerated`.
- **`IsFaithful`**: The action is faithful: only `r = 0` annihilates every element.

#### Counting and Zero-Sums (in `\where` of `LModule`)

- **`count`**: For `l : Array (\Sigma R J)` and `j : J`, sums the coefficients tagged with `j`.
- **`count_zro`**: `count l j = 0` when `j` doesn't appear in `l`.
- **`count-unique`**: For an injective indexing, `count` recovers the original coefficient.
- **`IsZeroSum`**: A formal sum is a zero-sum if (up to permutation) it groups into per-index blocks each summing to zero.
  - **`IsZeroSum.aux`**: Normalizes by removing empty coefficient blocks.
  - **`IsZeroSum.nonEmpty`**: Witness with all blocks nonempty.
- **`Z~`**: Equivalence of formal sums: `l1 Z~ l2` iff `negate(l2) ++ l1` is a zero-sum.
- **`count_IsZeroSum`**: If every distinct index has zero count, the sum is a zero-sum.
- **`IsZeroSum_count`**: Conversely, zero-sums have zero count at every index. Includes `count_EPerm`, `count_++`, `count_Big++` lemmas.
- **`basisSet-split`**: Given a basis `u : J -> U` and a parallel target `v : J -> V`, transports `x : U` to a unique `y : V` along the same coefficients (well-defined by `Z~`).

#### Module Constructions (in `\where` of `LModule`)

- **`pullback`**: Given `f : RingHom` and an `f.Cod`-module `M`, produces an `f.Dom`-module on the same underlying group via `r *c x := f r *c x`.
- **`generated-fin-comp`**: If `f.Cod` is finitely generated over `f.Dom` and `M` is finitely generated over `f.Cod`, then `M` (as `f.Dom`-module) is generated by the product family.
- **`generated-pullback`**: Pullback preserves finite generation under composition.
- **`generated-comp`**: Finite generation composes along ring homomorphisms.

#### Product Modules

- **`ProductLModule`**: The product `A × B` of `R`-modules with componentwise scalar action.
- **`ProductLModule.in1`**, **`ProductLModule.in2`**: Coproduct injections as `LinearMap`s into the product.
- **`ProductLModule.proj1`**, **`ProductLModule.proj2`**: Product projections as `LinearMap`s.
- **`ProductLModule.coprod-map`**: From `i : A -> C` and `j : B -> C`, builds the cotuple `A × B -> C`.
- **`ProductLModule.prod-map`**: From `a : C -> A` and `b : C -> B`, builds the pairing `C -> A × B`.

#### Other Module Instances

- **`RingLModule`**: The ring `R` as a left module over itself, with `*c := *`.
  - **`*_hom-left`**: Left multiplication by `x` as a linear map (commutative case).
  - **`*_hom-right`**: Right multiplication by `x` as a linear map.
  - **`basis`**: `(1 :: nil)` is a basis of `R` over itself.
- **`ArrayLModule`**: Length-`n` arrays over a module `M` form an `R`-module under pointwise operations.
  - **`skip_*c`**, **`skip_+`**: Compatibility of `skip` (drop-index) with scalar multiplication and addition.
  - **`BigSum-index`**: `BigSum` of arrays evaluated at index `i` equals the `BigSum` of pointwise evaluations.
- **`homLModule`**: Given `f : RingHom`, makes `f.Cod` into an `f.Dom`-module via `x *c y := f x * y`.
