### Data.Array.Pairs

Cartesian-product-like operation on arrays, applying a binary function to every pair of elements from two input arrays.

The `pairs` function builds an array containing `f a b` for every `a` in `l` and every `b` in `l'`, with the second-argument loop nested inside the first-argument loop. Most of the module establishes the algebraic properties of this construction: it is associative and distributive (when `f` is), commutativity holds up to extended permutation (`EPerm`), and indexing into the resulting flat array corresponds bijectively to choosing a pair of indices. These lemmas make `pairs` suitable for formalizing finite sums of products and other "double sum" arguments.

#### Core Definition

- **`pairs`**: Given `f : A -> B -> C`, `l : Array A`, `l' : Array B`, produces the flattened array of `f a b` over all `a ∈ l`, `b ∈ l'`. Defined by recursion on `l`: cons `a :: l` yields `map (f a) l' ++ pairs f l l'`.

#### Boundary and Symmetry Lemmas

- **`pairs_nil`**: `pairs f l nil = nil` — empty second argument yields the empty array.
- **`pairs-flip`**: `EPerm (pairs f l l') (pairs (\lam b a => f a b) l' l)` — swapping argument order produces a permutation-equivalent array.

#### Concatenation Compatibility

- **`pairs_++-left`**: `pairs f (l1 ++ l2) l = pairs f l1 l ++ pairs f l2 l` — strict equality on the left argument.
- **`pairs_++-right`**: `EPerm (pairs f l (l1 ++ l2)) (pairs f l l1 ++ pairs f l l2)` — distribution on the right holds only up to `EPerm` (because of interleaving order).

#### Interaction with `map`

- **`pairs_map-left`**: When `f` is associative, `pairs f (map (f a) l) l' = map (f a) (pairs f l l')` — left-multiplication by `a` factors through `pairs`.
- **`pairs_map`**: For a homomorphism `g : A -> B` between binary operations `f1` and `f2`, `map g (pairs f1 l l') = pairs f2 (map g l) (map g l')`.

#### Associativity

- **`pairs-assoc`**: For associative `f`, `pairs f (pairs f l1 l2) l3 = pairs f l1 (pairs f l2 l3)` — `pairs` itself is associative on associative operations.

#### Indexing

- **`pairs-index`**: Given `i : Fin l.len`, `j : Fin l'.len`, returns an index `k` into `pairs f l l'` together with a proof that `(pairs f l l') k = f (l i) (l' j)`.
- **`pairs-index-inj`**: The map `(i, j) ↦ k` is injective: equal output indices force `i = i'` and `j = j'`.
- **`pairs-index-surj`**: The map is surjective onto `Fin (length of pairs f l l')`.
- **`pairs-index-equiv`**: Combining injectivity and surjectivity, `(Fin l.len) × (Fin l'.len)` is equivalent to the index set of `pairs f l l'`.

#### Distributivity over Sums

- **`pairs-distr`**: In a `Semiring` `R`, if `g : A -> R` satisfies `g (f a b) = g a * g b`, then `BigSum (map g (pairs f l l')) = BigSum (map g l) * BigSum (map g l')` — the key lemma turning `pairs` into a product of sums (e.g. for polynomial multiplication).
