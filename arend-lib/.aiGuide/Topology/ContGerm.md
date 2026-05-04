### Topology.ContGerm

Continuous germs at a point: equivalence classes of continuous maps defined on open neighborhoods of a point, with algebraic structure inherited from the target.

#### Core Type

- **`ContGerm`**: `(X Y : TopSpace) (x : X) -> \Type`. The type of germs at `x` of continuous maps `X -> Y`, defined as the quotient of pairs `(U, Uo, Ux, f)` (open neighborhood `U` of `x` together with a continuous map `f : TopSub U -> Y`) by the equivalence identifying maps that agree on some smaller open neighborhood of `x`.
- **`ContGerm.equivalence`**: The underlying equivalence relation: `f ~ g` iff there is an open `V ∋ x` contained in both domains where `f` and `g` agree pointwise.
- **`ContGerm.comp-right`**: Post-composition `ContGerm X Y x -> ContMap Y Z -> ContGerm X Z x` of a germ with a globally continuous map.
- **`ContGerm.tuple`**: Pairs two germs at `x` into a germ valued in the product: `ContGerm X Y x -> ContGerm X Z x -> ContGerm X (Y ⨯ Z) x`, using the intersection of their representing neighborhoods.

#### Constructors and Equality

- **`inCG`**: Builds a germ from an open neighborhood `U` of `x` and a continuous map `f : TopSub U -> Y`.
- **`inCGt`**: Embeds a globally continuous map `f : ContMap X Y` as a germ at `x` (via the trivial total neighborhood).
- **`~-cgequiv`**: Equality lemma for germs: two representatives give equal germs whenever they agree on some common open neighborhood `V ∋ x`.
- **`~-cgequiv.conv`**: Converse of `~-cgequiv` — extracts a witnessing common neighborhood from a germ equality.

#### Evaluation

- **`evalCG`**: Evaluates a germ at its base point: `ContGerm X Y x -> Y`. Well-defined by the equivalence relation.

#### Algebraic Structure

- **`ContGermAbGroup`**: `AbGroup` instance on `ContGerm X Y x` when `Y : TopAbGroup`. Zero is the constant-zero germ; addition is `tuple` followed by `+-cont`; negation is post-composition with `negative-cont`.
- **`ContGermRing`**: `Ring` instance on `ContGerm X Y x` when `Y : TopRing`. Extends `ContGermAbGroup`; identity is the constant-one germ; multiplication uses `tuple` and `*-cont`.
- **`ContGermLModule`**: `LModule` instance making `ContGerm X Y x` a module over `ContGermRing X R x` when `Y : TopLModule R`. Extends `ContGermAbGroup`.

#### Homomorphism Lemmas

- **`evalCGAddGroupHom`**: Evaluation `evalCG : ContGermAbGroup X Y x -> Y` is an additive group homomorphism.
- **`evalCG-linear`**: Compatibility of `evalCG` with scalar multiplication: `evalCG (inCGt (const r) *c g) = r *c evalCG g`.
- **`cg-module-inj`**: Injectivity of multiplication by `inCGt id` on germs at `0` for a Hausdorff module over a near skew field: if `inCGt id *c f = inCGt id *c g`, then `f = g`.
