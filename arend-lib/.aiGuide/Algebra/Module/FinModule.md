### Algebra.Module.FinModule

Finitely generated free modules over a ring, equipped with a basis, and their dimension theory.

A `FinModule` is an `LModule` together with a (truth-valued) witness that some finite array of elements forms a basis. The module develops the standard linear-algebra consequences: surjections from finite modules can be lifted/split, any two bases have the same length (over a non-zero commutative ring), giving a well-defined `dimension`, and surjective endomorphisms between equidimensional finite modules are isomorphisms. The standard example `R^n` is shown to be a `FinModule` with basis the columns of the identity matrix.

#### Core Class

- **`FinModule`**: Extends `LModule` with the propositional witness `isFinModule : ∃ (l : Array E) (IsBasis l)` — a module that admits some finite basis.

#### Lifting and Splitting Surjections

- **`surj-lift`**: For a `FinModule` `F` over a ring `R`, any linear map `f : F → V` factors through any surjection `g : U ↠ V`; produces `h : F → U` with `g ∘ h = f`.
- **`surj-split`**: A surjection `g : U ↠ V` onto a `FinModule` `V` admits a linear section `h : V → U` with `g ∘ h = id`.

#### Dimension Theory (over `NonZeroCRing`)

- **`basis<=generating`**: If `l` is a basis and `l'` generates `U`, then `l.len <= l'.len`. The Steinitz-style exchange bound underlying invariance of dimension.
- **`dimension-pair`**: Packages the dimension together with a basis-existence proof as a proposition (any two such pairs are equal), making the natural number well-defined.
- **`dimension`**: The dimension of a `FinModule` over a non-zero commutative ring, extracted from `dimension-pair`.
- **`dimension-char`**: Existence of a basis of length exactly `dimension U`.
- **`dimension-unique`**: Any basis `l` of `U` has length equal to `dimension U`.
- **`dimension=0`**: Characterizes zero-dimensional modules as the trivial modules: `dimension U = 0 ↔ ∀ a, a = 0`.

#### Isomorphism Criteria

- **`surj-iso`**: A surjective linear map between `FinModule`s of equal dimension is an isomorphism in `LModuleCat R`.
- **`surj-iso.basis-surj-iso`**: Helper showing that over a `CRing`, a surjection between modules with bases of equal length `n` is an isomorphism — the matrix-level core of `surj-iso`.

#### Standard Example

- **`ArrayFinModule`**: Instance making `Array R n` a `FinModule R` via the `ArrayLModule` structure on the regular module `RingLModule R`.
- **`ArrayFinModule.basis`**: The rows/columns of the identity matrix `MatrixRing.ide` form a basis of `R^n`.
