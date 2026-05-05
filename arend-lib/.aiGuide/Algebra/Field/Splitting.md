### Algebra.Field.Splitting

Splitting fields of polynomials over discrete fields, including a construction of countable splitting fields via iterated root field extensions.

#### Splitting Field Definition

- **`IsSplittingField`**: Predicate stating that a ring homomorphism `f : k -> K` is a splitting field for `p : Poly k`, meaning `K` is generated as a `k`-algebra by elements `l` and `polyMap f p` factors as `c * ∏ (X - aᵢ)` over `K`.

#### Properties of Splitting Fields

- **`splitting-integral`**: A splitting field extension `f : k -> K` is integral, i.e. every element of `K` is a root of some polynomial over `k`.
- **`splitting-monic`**: For a monic polynomial `p`, its splitting field factorization can be taken with leading coefficient `1`, yielding `polyMap f p = ∏ (X - aᵢ)`.

#### Countable Splitting Field Construction

- **`countableSplittingField`**: Given a countable discrete field `k` and a polynomial `p : Poly k`, constructs a countable discrete field `K` with a ring homomorphism `f : k -> K` making `K` a splitting field of `p`.

#### Abstract Splitting Field Machinery

- **`RootFieldData`**: Class packaging the data of a root field extension: a property `P` preserved by the construction, a base field `k`, a polynomial `p`, an extension field `RootField` with property `P`, a homomorphism `rootFieldHom`, a distinguished `root` of `p` in `RootField`, and a generation property showing every element is `q(root)` for some `q : Poly k`.
- **`SplittingFieldData`**: Class providing the inductive ingredients to build splitting fields: a class property `P`, an auxiliary predicate `S` on polynomials closed under homomorphisms (`S-hom`) and factors (`S-factor`), and an operation `nextField` producing a `RootFieldData` for any non-zero non-invertible polynomial.
- **`CountableSplittingFieldData`**: Instance of `SplittingFieldData` with `P = Countable`, building each next field as the factor field `Poly k / m` where `m` is a maximal ideal containing `p`, using `factor-countable` to preserve countability.
- **`poly-eval`**: Lemma that evaluating `polyMap polyHom p` at the generic element `padd 1 0` (i.e. `X`) recovers `p` itself.
