### Algebra.Group.QuotientProperties

Universal properties and isomorphism theorems for group quotients by normal subgroups.

#### Quotient Notation

- **`//`**: Infix notation for the quotient group `G // H` where `H` is a normal subgroup of `G`, defined as `H.quotient`.

#### Diagram Structures

- **`GroupTriangle`**: A commutative triangle of group homomorphisms, with groups `X, Y, Z`, maps `f : X -> Y`, `g : Y -> Z`, `h : X -> Z`, and a proof `comm : h = g ∘ f`.
- **`UniversalGroupQuotient`**: Data for the universal property of a quotient: a homomorphism `f : G -> H` and a normal subgroup `N` of `G` contained in the kernel of `f` (`p : N <= f.Kernel`).
- **`FirstIsomorphismTheorem`**: Bundles the data `G`, `H`, and a homomorphism `f : G -> H` for stating the first isomorphism theorem.

#### First Isomorphism Theorem Corollaries

- **`GroupFirstIsoCorollary-setwise`**: Given `f : G -> H` with `N = f.Kernel` and `f` surjective, produces a homomorphism `G // N -> H` together with a proof that it is an isomorphism (set-theoretic version).
- **`GroupFirstIsoCorollary`**: Same as above, but packages the result as an `Iso` in the category `GroupCat`, using `GroupCat.Iso<->Inj+Surj` to convert between the two notions of isomorphism.

#### Helper Constructions

- **`GroupFirstIsoCorollary-setwise.GroupFirstIsoCorollary-2`**: Constructs the canonical homomorphism `G // f.Kernel -> H` via the universal property from `FirstIsomorphismTheorem.univ`.
- **`GroupFirstIsoCorollary-setwise.GroupFirstIsoCorollary-24`**: Pairs the canonical map `G // f.Kernel -> H` with a proof that it is an isomorphism when `f` is surjective.
- **`GroupFirstIsoCorollary-setwise.GroupFirstIsoCorollary-3`**: Transports the canonical map along an equality `N = f.Kernel` to obtain a homomorphism `G // N -> H`.
