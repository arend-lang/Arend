### Relation.Equivalence

Transitive relations, equivalence relations, and their set-quotients.

This module formalizes equivalence-like relations as classes (`Transitive` and `Equivalence`) carrying a propositional relation `~` together with the appropriate closure properties. For arbitrary relations `R`, free transitive and equivalence closures are constructed inductively (`Transitive.Closure`, `Equivalence.Closure`) with universal-property lemmas that let one factor through any existing structure. The `Quotient` higher-inductive type identifies `R`-related elements as a `\Set`, and the file connects path equality in the quotient back to the closure relation, providing the standard toolkit (surjectivity of `in~`, lifting of functions, choice on quotients, decidable equality).

#### Transitive Relations

- **`Transitive`**: Class on a `\Set A` with a propositional relation `~` and a transitivity proof `~-transitive`.
- **`Transitive.Closure`**: Inductive free transitive closure of an arbitrary relation `R`, with constructors `cin` (inclusion) and `ctrans` (transitive step); valued in `\Prop`.
- **`Closure.isTransitive`**: Packages `Closure R` as a `Transitive` instance on `A`.
- **`Closure.isSymmetric`**: If `R` is symmetric, so is its transitive closure.
- **`Closure.univ`**: Universal property — any `R`-respecting map into a `Transitive` `E` extends to the closure.
- **`Closure.ofTransitive`**: Specialization where `R` is the relation `~` of an existing `Transitive` structure; the closure collapses to `~`.
- **`Closure.toEquality`**: If `f : A -> B` identifies `R`-related elements, then `f` identifies closure-related elements.

#### Equivalence Relations

- **`Equivalence`**: Class extending `Transitive` with reflexivity (`~-reflexive`) and symmetry (`~-symmetric`).
- **`Equivalence.map`**: Pulls back an equivalence on `B` along `f : A -> B` to an equivalence on `A`.
- **`Equivalence.Closure`**: Inductive free equivalence closure of `R`, with constructors `cin`, `crefl` (from path equality), `csym`, and `ctrans`.
- **`Closure.isEquivalence`**: Packages `Closure R` as an `Equivalence` instance.
- **`Closure.univ`**: Universal property — any `R`-respecting map into an `Equivalence` extends to the closure.
- **`Closure.ofEquivalence`**: Specialization where `R = ~` for an existing `Equivalence`.

#### Quotients

- **`Quotient`**: Set-quotient HIT of a type `A` by a relation `R`, with point constructor `in~` and path constructor `~-equiv` identifying related elements.
- **`~-pequiv`**: Convenience wrapper turning `R x y` into the path `in~ x = in~ y`.
- **`Quotient.in-surj`**: `in~` is surjective onto the quotient.
- **`Quotient.equality`**: Extracts an equivalence-`Closure R` proof from a path `in~ x = in~ y`.
- **`Quotient.equalityEquiv`**: Specialization for an `Equivalence` — a path in the quotient gives `x ~ y` directly.
- **`Quotient.equalityClosure`**: Variant where `R` refines an equivalence `~` via a witness `s`.
- **`Quotient.fromEquality`**: Converts an equivalence-`Closure R` proof into a path in the quotient.
- **`Quotient.map`**: Functorial action — lifts `f : A -> B` mapping `R` into `Q` to a function `Quotient R -> Quotient Q`.
- **`Quotient.liftArray`**: Lifts an array of quotients to a quotient of arrays under the pointwise relation, given reflexivity of `R`.
- **`Quotient.liftArrayFun`**: Lifts a function `DArray A -> B` that respects pointwise `R` to a function on quotient-valued arrays.
- **`Quotient.liftChoice`**: Choice principle — if the domain is a `Choice` set, a family of quotient values can be lifted to a family of representatives.

#### Subquotients and Decidability

- **`SubQuotient`**: Quotient restricted to elements `a` satisfying `R a a` (the "reflexive part"), useful for partial equivalence relations.
- **`QuotientDec`**: Builds a `DecSet` structure on `Quotient ~` from a decision procedure for the underlying equivalence `~`.
