### Set.Fin.KFinSetOf

Kuratowski-finite subsets of a set, as a record with closure operations.

`KFinSetOf X P` packages a predicate `P` on a set `X` with the statement that `P` is merely the set of entries of some array (`isFinSubSet`), i.e. a Kuratowski-finite subset. The module shows that such a subset can be enumerated by a surjection from `Fin n` and so gives a `KFinSet`, and proves that Kuratowski-finite subsets contain the empty set, singletons and arrays, and are closed under finite unions, images along relations, preimages under injective maps, and splitting along a decidable cover.

#### The record

- **`KFinSetOf`**: A set `X` with a predicate `P : X -> \Prop` such that there merely is an array `l` of elements of `X` with `P x` iff `x` is in the image of `l` (`TSetIm`).
- **`KFinSetOf.enumerate`**: A Kuratowski-finite subset is merely enumerated by a surjection from `Fin n` onto the elements satisfying `P`.
- **`KFinSetOf.toKFin`**: The type of elements satisfying `P` is merely a `KFinSet`.

#### Constructions

- **`KFinSetOf.empty`**: The empty subset.
- **`KFinSetOf.single`**: A singleton `x =`.
- **`KFinSetOf.fromArray`**: The set of entries of an array.
- **`KFinSetOf.union`**: The union of two Kuratowski-finite subsets.
- **`KFinSetOf.BigUnion`**: The union of a family of such subsets indexed by `Fin n`.
- **`KFinSetOf.FinUnion`**: The union of a family of such subsets indexed by a `KFinSet`.
- **`KFinSetOf.preimage`**: The preimage `A (f x)` under an injective map `f`, provided every element of `A` is in the image of `f`.
- **`KFinSetOf.image`**: For a relation `R` and a finite `A` whose elements all have an `R`-related element, a finite `B` such that every element of `A` is related to one of `B` and every element of `B` to one of `A`.

#### Splitting

- **`KFinSetOf.DecUnion-split`**: If a finite `A` is covered by a family `S` indexed by a decidable set `I`, there are finite pieces `T i` which cover `A` and lie in `A` and `S i`.
- **`KFinSetOf.union-split`**: If a finite `A` is contained in `B ∪ C`, there are finite `B'` and `C'` covering `A`, with `B'` inside `A` and `B`, and `C'` inside `A` and `C`.
