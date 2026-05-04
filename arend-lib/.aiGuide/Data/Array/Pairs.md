### Data.Array.Pairs

This module defines the `pairs` operation (Cartesian-product-like combination of two arrays) and its properties.

#### Pairs Function

- **`pairs`**: Given `f : A -> B -> C` and arrays `l : Array A`, `l' : Array B`, produces an array of all `f (l i) (l' j)`.

#### Basic Properties

- **`pairs_nil`**: `pairs f l nil = nil`.
- **`pairs-flip`**: `EPerm (pairs f l l') (pairs (flip f) l' l)`.
- **`pairs_++-left`**: `pairs f (l1 ++ l2) l = pairs f l1 l ++ pairs f l2 l`.
- **`pairs_++-right`**: `EPerm (pairs f l (l1 ++ l2)) (pairs f l l1 ++ pairs f l l2)`.

#### Map and Associativity

- **`pairs_map-left`**: Relates `pairs` with `map` on the left when `f` is associative.
- **`pairs_map`**: `map g (pairs f1 l l') = pairs f2 (map g l) (map g l')` when `g` commutes with `f1`/`f2`.
- **`pairs-assoc`**: Associativity of `pairs` when `f` is associative: `EPerm (pairs f (pairs f l1 l2) l3) (pairs f l1 (pairs f l2 l3))`.

#### Indexing

- **`pairs-index`**: Indexing into `pairs f l l'` via a pair `(i, j)`.
- **`pairs-index-inj`**: The index mapping `(i, j) -> pairs-index` is injective.
- **`pairs-index-surj`**: The index mapping is surjective.
- **`pairs-index-equiv`**: The index mapping is an equivalence.

#### Distribution over BigSum

- **`pairs-distr`**: If `g (f a b) = g a * g b`, then `BigSum (map g (pairs f l l')) = BigSum (map g l) * BigSum (map g l')`.
