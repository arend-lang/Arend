### Category.Solver

A reflection-based solver for equalities of morphisms in a precategory, normalizing composition expressions to a canonical right-associated form.

This module provides the syntactic infrastructure for proving equations between composites of morphisms by normalization. Categorical terms (built from variables, identity, and composition) are reified as the inductive type `CatTerm`, then reduced to a flat list-like normal form `CatNF` that eliminates identities and reassociates compositions. The `HData` class packages an interpretation back into a concrete precategory, together with the soundness lemmas needed to turn syntactic equality of normal forms into semantic equality of morphisms — the standard pattern for tactic-style proof automation in dependent type theory.

#### Syntactic Term Representation

- **`CatTerm`**: Reified categorical expressions over a vertex set `V` and an edge family `H : V -> V -> \Type`, with constructors `var` (a generator), `:id` (identity, parameterized by an equality of endpoints), and `:o` (composition through an intermediate object).
- **`CatNF`**: Normal forms as right-associated lists of generators, built from `:nil` (an identity coerced along an endpoint equation) and `:cons` (prepending a generator).

#### Normalization

- **`normalize`**: Converts a `CatTerm a b H` into a `CatNF a b H` by flattening compositions and erasing identities.
- **`normalize.aux`**: Tail-recursive worker that accumulates the result, processing the right operand of each composition before the left so that the output is right-associated.

#### Semantic Interpretation (`HData`)

- **`HData`**: Bundles a precategory `C`, a vertex map `f : V -> C`, a generator family `H`, and an interpretation `g` of generators as morphisms `Hom (f x) (f y)`. Provides the bridge between syntax and the target precategory.
- **`interpret`**: Evaluates a `CatTerm a b H` to a morphism `Hom (f a) (f b)` using `id` and `∘`.
- **`interpretNF`**: Evaluates a `CatNF a b H` to a morphism, special-casing singleton lists to avoid an extra identity composition.
- **`interpretNF.cons`**: States that `interpretNF (h :cons l) = g h ∘ interpretNF l`, smoothing over the singleton special case in the definition.

#### Concatenation and Soundness

- **`:++`**: Append on normal forms, corresponding to composition of the represented morphisms.
- **`interpretNF_++`**: Soundness of append: `interpretNF (h :++ h') = interpretNF h ∘ interpretNF h'`.
- **`normalize-consistent`**: Main soundness lemma — `interpret t = interpretNF (normalize t)`, so a term and its normal form denote the same morphism.
- **`normalize-consistent.aux`**: Generalized statement for the accumulator-passing `normalize.aux`, asserting `interpret t ∘ interpretNF acc = interpretNF (normalize.aux t acc)`.

#### Decision Procedure

- **`terms-equality`**: The user-facing solver entry point: given two terms `t, s` whose normal forms are equal, derives `interpret t = interpret s`. Used to discharge categorical equations by reducing both sides to the same canonical list of generators.
