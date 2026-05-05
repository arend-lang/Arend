### Algebra.Module.FinModule

Finitely generated (free) modules over a ring, equipped with a finite basis, along with dimension theory and lifting/splitting properties.

#### Main Class

- **`FinModule`**: Extends `LModule` with the property `isFinModule` asserting the merely existence of a finite array of elements forming a basis.

#### Lifting and Splitting Lemmas

- **`surj-lift`**: Given a finite module `F`, any linear map `f : F -> V` lifts through a surjective linear map `g : U -> V`, yielding `h : F -> U` with `g ∘ h = f`.
- **`surj-split`**: Any surjection `g : U -> V` onto a finite module `V` admits a linear section `h : V -> U` with `g ∘ h = id`.

#### Dimension Theory (over `NonZeroCRing`)

- **`basis<=generating`**: Any basis is no larger than any generating set: `l.len <= l'.len` when `l` is a basis and `l'` generates.
- **`dimension-pair`**: The dimension paired with a proof of basis existence, packaged at `\level` to make it a proposition (any two such pairs are equal via the antisymmetry of the basis-vs-generating bound).
- **`dimension`**: The dimension of a finite module as a `Nat`, extracted from `dimension-pair`.
- **`dimension-char`**: There merely exists a basis of length `dimension U`.
- **`dimension-unique`**: Any basis of `U` has length equal to `dimension U`.
- **`dimension=0`**: A finite module has dimension `0` iff it is trivial (every element equals `0`).
- **`surj-iso`**: A surjective linear map between finite modules of equal dimension is an isomorphism in `LModuleCat R`.
  - **`basis-surj-iso`**: Helper showing that a surjection between modules with bases of the same length `n` is an isomorphism (over a commutative ring).

#### Standard Examples

- **`ArrayFinModule`**: The free module `R^n = Array R n` as a `FinModule R`, extending `ArrayLModule` with the standard basis.
  - **`basis`**: The rows of the identity matrix `MatrixRing.ide` form a basis of `Array R n`.
