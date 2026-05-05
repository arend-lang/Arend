### Relation.Equivalence

Equivalence relations, transitive closures, and quotient types.

#### Transitive Class and Closure

- **`Transitive`**: Class with carrier `A : \Set`, relation `~`, and `~-transitive`.
  - **`Closure`**: Truncated transitive closure of a relation `R`, with `cin` and `ctrans`.
    - **`isTransitive`**: `Closure R` forms a `Transitive`.
    - **`isSymmetric`**: If `R` is symmetric, so is `Closure R`.
    - **`univ`**: Universal property: maps into any `Transitive`.
    - **`ofTransitive`**: `Closure` of an already-transitive relation collapses.
    - **`toEquality`**: Maps closure to equality via a function preserving `R`.

#### Equivalence Class and Closure

- **`Equivalence`**: Class extending `Transitive` with `~-reflexive` and `~-symmetric`.
  - **`map`**: Pulls back an equivalence along a function `f : A -> B`.
  - **`Closure`**: Full equivalence closure with `cin`, `crefl`, `csym`, `ctrans`.
    - **`isEquivalence`**: `Closure R` forms an `Equivalence`.
    - **`univ`**: Universal property into any `Equivalence`.
    - **`ofEquivalence`**: Closure of an equivalence collapses.

#### Quotient Type

- **`Quotient`**: HIT quotient `Quotient R` with `in~` constructor and `~-equiv` path constructor.
  - **`in-surj`**: `in~` is surjective.
  - **`equality`**: `in~ x = in~ y` implies `Closure R x y`.
  - **`equalityEquiv`**: For an `Equivalence`, `in~ x = in~ y` implies `x ~ y`.
  - **`equalityClosure`**: Variant with an intermediate relation.
  - **`fromEquality`**: `Closure R x y` implies `in~ x = in~ y`.
  - **`map`**: Functorial action on quotients.
  - **`liftArray`**: Lifts an array of quotients to a quotient of arrays.
  - **`liftArrayFun`**: Lifts a function on arrays through quotients.
  - **`liftChoice`**: Choice-based lifting for quotients.
- **`~-pequiv`**: Shorthand for `path (~-equiv x y r)`.
- **`SubQuotient`**: Quotient restricted to elements satisfying `R a a`.
- **`QuotientDec`**: `DecSet` instance for `Quotient ~` when `~` is decidable.
