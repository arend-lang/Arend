### Category.Solver

A reflection-based solver for equations in a precategory, normalizing categorical expressions built from variables, identities, and compositions.

#### Term Representation

- **`CatTerm`**: Syntactic representation of a morphism expression in a category. Parameterized by a vertex type `V`, source/target objects `a b : V`, and a hom-family `H : V -> V -> \Type`.
  - **`var`**: Embeds an atomic morphism `H a b` as a term.
  - **`:id`**: Identity morphism, witnessed by an equality `a = b`.
  - **`:o`**: Composition through an intermediate object `c`, combining `CatTerm c b H` with `CatTerm a c H`.

#### Normal Form

- **`CatNF`**: Normal-form representation as a right-nested list of composable morphisms from `a` to `b`.
  - **`:nil`**: Empty composition, witnessed by `a = b` (acts as identity).
  - **`:cons`**: `\infixl 7` constructor prepending a morphism `H c b` onto a normal form ending at `c`.

#### Normalization

- **`normalize`**: Converts a `CatTerm a b H` into a `CatNF a b H` by flattening compositions and removing identities. Implemented via `aux` with an accumulator initialized to `:nil idp`.
  - **`normalize.aux`**: Accumulator-passing worker that traverses the term, emitting `var` nodes via `:cons`, dropping `:id idp`, and recursively processing both sides of `:o` so that compositions become a flat right-nested list.

#### Semantic Interpretation

- **`HData`**: Class bundling the data needed to interpret terms in a concrete precategory. Provides:
  - **`C : Precat`**: The ambient precategory.
  - **`V : \Set`**: An index set of objects.
  - **`f : V -> C`**: Mapping from indices to objects of `C`.
  - **`H : V -> V -> \Set`**: A family of atomic morphism names.
  - **`g`**: Interpretation of each name `H x y` as an actual morphism `Hom (f x) (f y)` in `C`.
