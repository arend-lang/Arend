### Topology.ContGerm

Continuous germs at a point — equivalence classes of locally-defined continuous maps that agree on some open neighborhood of the basepoint.

A `ContGerm X Y x` is the quotient of pairs `(U, continuous f : U → Y)` (with `U` an open neighborhood of `x`) by the equivalence that identifies two such maps when they coincide on a common smaller open neighborhood of `x`. This captures the standard notion of germ from sheaf theory in a constructive setting: only local behavior near `x` matters. The module further shows that germs inherit algebraic structure pointwise (abelian group, ring, module) from the codomain, and provides evaluation at the basepoint as a homomorphism, together with an injectivity result for the multiplication-by-identity germ in the field/Hausdorff setting.

#### Core Type

- **`ContGerm`**: The type of continuous germs of maps `X → Y` at `x : X`, defined as the quotient of `(U : Set X, isOpen U, U x, ContMap (TopSub U) Y)` by the local-agreement equivalence.
- **`ContGerm.equivalence`**: The underlying equivalence relation: two local maps are related if they agree on some common open neighborhood of `x` contained in both their domains.

#### Constructors and Operations

- **`inCG`**: Builds a germ from an open neighborhood `U` of `x` and a continuous map `f : TopSub U → Y`.
- **`inCGt`**: Builds a germ from a globally defined continuous map `f : ContMap X Y` by restricting to the trivial open `\Sigma`.
- **`ContGerm.comp-right`**: Postcomposes a germ `g : ContGerm X Y x` with a continuous map `h : Y → Z` to obtain `ContGerm X Z x`.
- **`ContGerm.tuple`**: Pairs two germs `f : ContGerm X Y x` and `g : ContGerm X Z x` into `ContGerm X (Y ⨯ Z) x`, intersecting their domains of definition.

#### Equivalence Lemmas

- **`~-cgequiv`**: Standard introduction rule for germ equality: two pairs represent the same germ when their underlying maps agree on a common open neighborhood `V` of `x`.
- **`~-cgequiv.conv`**: Converse — extracts the witnessing common open neighborhood and pointwise equality from a germ equality.

#### Evaluation

- **`evalCG`**: Evaluates a germ at the basepoint, producing `evalCG g : Y`. Well-defined on the quotient because all representatives agree at `x`.

#### Algebraic Structures on Germs

- **`ContGermAbGroup`**: Abelian group structure on `ContGerm X Y x` for `Y : TopAbGroup`. Addition is pointwise via `tuple` followed by the continuous addition `+-cont`; the zero germ is the constant `0` map; negation uses `negative-cont`.
- **`ContGermRing`**: Ring structure on `ContGerm X Y x` for `Y : TopRing`, extending `ContGermAbGroup`. Multiplication is pointwise via `tuple` followed by `*-cont`; the identity germ is the constant `1`.
- **`ContGermLModule`**: Left module structure on `ContGerm X Y x` over the ring `ContGermRing X R x`, where `Y : TopLModule R`. Scalar multiplication is defined on representatives via `*c-cont` and lifted through both quotient arguments.

#### Homomorphism and Injectivity Results

- **`evalCGAddGroupHom`**: Evaluation `evalCG` is an additive group homomorphism `ContGermAbGroup X Y x → Y`.
- **`evalCG-linear`**: Compatibility of evaluation with scalar multiplication: `evalCG (inCGt (const r) *c g) = r *c evalCG g`.
- **`cg-module-inj`**: Cancellation/injectivity: over a near skew-field `R` with Hausdorff target `Y`, multiplication by the identity germ is injective on `ContGerm R Y 0` — i.e., `inCGt id *c f = inCGt id *c g` implies `f = g`.
