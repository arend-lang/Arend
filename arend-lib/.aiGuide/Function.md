### Function

Basic definitions for working with functions: identity, composition, injectivity, surjectivity, and images.

This module provides the fundamental combinators used pervasively throughout the library. Function composition comes in three flavors — the standard infix `o`, plus partially-applied variants `-o` and `o-` that fix one of the two functions, which is useful when passing composition into higher-order constructions. Injectivity and surjectivity are defined propositionally, with surjectivity using a propositional truncation `∃` so that it is a property rather than structure. The `Image` type packages the surjective-image of a function as the subset of its codomain hit by some input.

#### Identity and Composition

- **`id`**: The identity function `\lam x => x` on any type `A`.
- **`o`** (infixr 8): Standard function composition `g o f = \lam x => g (f x)`.
- **`-o`**: Composition with the left function fixed: given `f : A -> B`, returns `\lam g x => g (f x)`, i.e. precomposition by `f`.
- **`o-`**: Composition with the right function fixed: given `g : B -> C`, returns `\lam f x => g (f x)`, i.e. postcomposition by `g`.

#### Injectivity and Surjectivity

- **`IsInj`**: Predicate that `f : A -> B` is injective: `f a = f a' -> a = a'` (defined on `\Set`s so the conclusion is a proposition).
- **`IsSurj`**: Predicate that `f : A -> B` is surjective: for every `y : B`, there merely exists an `x : A` with `f x = y`, using propositional truncation `∃`.
- **`IsSurj.comp`**: Composition of surjections is surjective: if `f` and `g` are surjective, so is `g o f`.

#### Images and Utilities

- **`Image`**: The image of `f : A -> B`, defined as `\Sigma (b : B) (∃ (a : A) (f a = b))` — points in the codomain that are merely hit by some input.
- **`assuming`**: Combinator `(f : (A -> B) -> B) (g : A -> B) => f g`, applying a higher-order function to a given argument; useful as a continuation-style helper.
- **`flip`**: Meta swapping the first two arguments of a binary function: `flip f a b` reduces to `f b a`.
