### Data.Array

Operations and lemmas for finite arrays (length-indexed sequences).

This module develops the basic theory of `Array A` (length-indexed vectors) and its dependent variant `DArray`. It provides constructors and destructors (`mkArray`, `tail`, `::`, `nil`), two flavors of concatenation (`++` for arrays of arbitrary length and `++'` when the right length is statically known), and a suite of structural operations: `map`, `filter`/`filterMap`, decidable `keep`/`remove`, `nub`, `insert`/`skip`/`replace`, `take`, `replicate`, and indexed access. The design centers on indexing by `Fin`, with many lemmas characterizing how operations behave on indices (e.g. `++_index-left`, `skip-index`, `insert-index`) so that array equalities can be reduced to pointwise equalities via `arrayExt`.

#### Construction and Extensionality

- **`mkArray`**: Build an `Array A n` from a function `Fin n -> A`.
- **`arrayExt`**: Extensionality: pointwise equal arrays are equal.
- **`array-unext`**: Inverse of extensionality, extracts pointwise equality from a path.
- **`replicate`**: Constant array of length `n` with value `a`.
- **`replicate_+`**: `replicate (n+m) a = replicate n a ++ replicate m a`.
- **`singleAt`**: Array that takes value `value` at index `j` and `def` elsewhere; `alt` is an inductive alternative form, `char` proves they agree.

#### Head/Tail Destructors

- **`tail`**: Drops the first element of an array.
- **`taild`**: Tail for dependent arrays.
- **`unhead`**: From `a :: l = a' :: l'` extract `a = a'` (uses helper `headDef`).
- **`untail`**: From `a :: l = a' :: l'` extract `l = l'` (uses helper `tailDef`).
- **`len=0`**: An array of length 0 equals `nil`.
- **`cong_::`**: Congruence for `::`.

#### Map

- **`map`**: Apply `f : A -> B` pointwise.
- **`map_::`**: `map f (a :: l) = f a :: map f l`.
- **`map_++`**, **`map_++'`**, **`map2_++'`**: `map` distributes over both concatenations.
- **`map_replace`**, **`map_insert`**, **`skip_map`**, **`fit_map`**: `map` commutes with `replace`, `insert`, `skip`, `fit`.

#### Concatenation

- **`++'`**: Concatenation when the right length is statically known; preserves `xs.len + n`.
- **`++'_index-left`**, **`++'_index-right`**: Indexing `++'` via `fin-inc`/`fin-raise`.
- **`++'.split-index`**: Decompose a `Fin (n+m)` as left or right.
- **`++'-split`**: Any array of length `n+m` is a `++'` of its halves.
- **`++`**: Concatenation of arrays of arbitrary length.
- **`++_++'`**: The two concatenations agree.
- **`++.index-left`**, **`++.index-right`**, **`++_index-left`**, **`++_index-right`**: Embed indices into the concatenation and characterize lookup.
- **`++.index-left-nat`**, **`++.index-right-nat`**, **`++.index-left-inj`**, **`++.index-right-inj`**, **`++.index-left/=right`**: Numerical and injectivity properties of the index embeddings.
- **`++.split-index`**: Every index of `l ++ m` comes from `l` or `m`.
- **`++.++-all`**: Lift a pointwise predicate from `l` and `l'` to `l ++ l'`.
- **`++.index-big`**, **`++.Big++-index`**: Indexing into a flattened array of arrays.
- **`++_nil`**: `l ++ nil = l`.
- **`++-assoc`**: Associativity.
- **`++-cancel-left`**, **`++-cancel-right`**: Cancellation laws.
- **`len_++`**: Length is additive.
- **`Big`**: Generic right-fold over an array.
- **`Big++_map`**, **`Big++-split`**: Interaction of flattening (`Big ++ nil`) with `map` and the singleton embedding.

#### Membership

- **`Index`**: `\Sigma (i : Fin l.len) (l i = x)` — proof that `x` occurs in `l`.
- **`index-left`**, **`index-right`**: Lift a membership witness across `++`.
- **`index-dec`**: Decide membership in an array over a `DecSet`.
- **`find`**: Search for the first index satisfying a decidable predicate, returning either the minimal witness with proof of minimality, or a proof of universal failure.
- **`repeats-dec`**: Decide whether an array has a repeated element, returning either the colliding indices or `IsInj l`.

#### Filtering and Removal

- **`filter`**: Keep elements satisfying a `Bool`-valued predicate.
- **`filter-sat`**: Every element of `filter p l` satisfies `p`.
- **`filter_true`**, **`filter_false`**: Filtering by an always-true/false predicate.
- **`filter-index`**: A satisfying element appears in `filter p l`.
- **`filterMap`**: Apply a partial map `A -> Maybe B` and collect the `just` results.
- **`filterMap-index`**: A `just`-mapped element appears in the result.
- **`keep`**: Decidable analogue of `filter` over a propositional predicate `P : A -> \Prop`.
- **`keep.satisfies`**, **`keep.element`**, **`keep.preimage`**, **`keep.no-repeats`**: Pointwise witness, surjectivity, preimage, and injectivity-preservation properties of `keep`.
- **`keep_++`**, **`keep-all`**, **`keep-none`**, **`keep-unique`**: Distributivity over `++` and degenerate cases.
- **`remove`**: Dual of `keep` — drops elements satisfying `P`.
- **`remove.no-element`**, **`remove.element`**, **`remove.preimage`**, **`remove.no-repeats`**, **`remove.equals`**: Analogous properties of `remove`, plus extensionality up to predicate equivalence.
- **`remove-none`**, **`remove_map`**, **`remove_remove`**, **`remove-swap`**: Degenerate cases, commutation with `map`, and double-`remove` identities.
- **`keep=remove`**, **`remove=keep`**: `keep` and `remove` are dual via `NotDec`.
- **`keep_remove=keep`**: When `P` and `Q` are disjoint, `keep DP` is unaffected by `remove DQ`.
- **`remove<=`**: `remove` does not increase length.
- **`removeElem`**: Remove all occurrences of a specific element.
- **`remove1`**: Remove (at most) one occurrence of `a` from a non-empty array, lowering the length by one.
- **`remove1-surj`**, **`remove1/=`**, **`remove1-inj`**: Surjectivity onto the original indices, non-occurrence of `a`, and injectivity preservation.

#### Counting

- **`count`**: Count occurrences of `a` in an array over a `DecSet`.
- **`count<=`**: `count l a <= l.len`.
- **`count_++`**, **`count_Big++`**: Additivity over `++` and over flattening.
- **`count-all`**, **`count-none`**: Saturated and empty counts.
- **`keep_count`**: `keep (decideEq a) l = replicate (count l a) a`.
- **`count_remove_yes`**, **`count_remove_no`**, **`count_remove`**: Effect of `remove` on counts.

#### Deduplication

- **`nub`**: Remove duplicate elements (left-to-right) over a `DecSet`.
- **`nub-isSurj`**: Every element of `l` appears in `nub l`; uses helper `nub.remove-isSurj`.
- **`nub-preimage`**: Every entry of `nub l` comes from `l`.
- **`nub-isInj`**: `nub l` is injective.
- **`nub_id`**: `nub` is the identity on injective arrays.

#### Insert / Skip / Replace

- **`insert`**: Insert `a` at position `j`, increasing length by one.
- **`insert_zro`**, **`insert-index`**: Inserting at `0` and the value at the inserted index.
- **`skip`**: Drop the element at position `k`, decreasing length by one.
- **`skip.newIndex`**: Reindex a `Fin (suc n)` distinct from `k` into `Fin n`.
- **`skip.newIndex_<`**: `newIndex` preserves order.
- **`skip_++'`**, **`skipExt`**, **`skip_0`**: `skip` distributes over `++'`, is determined pointwise, and behaves trivially at index 0.
- **`skip_replicate`**: `skip` on a `replicate` yields a shorter `replicate`.
- **`skip-index`**: Looking up `skip l k` at `newIndex p` recovers `l j`.
- **`replace`**: Update the element at index `i`.
- **`replace-index`**, **`replace-notIndex`**: Lookup at the replaced and unaffected indices.
- **`replace_insert`**: Replacing immediately after inserting.
- **`skip_replace_=`**, **`skip_replace_/=`**: Skipping a replaced array (same vs. different index).
- **`insert_skip`**: `insert a (skip l k) k = replace l k a`.
- **`skip_insert_=`**: `skip (insert a l j) j = l`.

#### Bool-Valued Quantification

- **`forall`**: Boolean conjunction of `p` over the array.
- **`forall-char`**: Reflects `forall p l = true` into pointwise truth.

#### Length Adjustment

- **`fit`**: Coerce/pad an array to length `n`, using `a` as the default for missing positions.
- **`fit_<`**, **`fit_<'`**, **`fit_>=`**: Behavior of `fit` below and at/above the original length.
- **`take`**: Prefix of length `n` of an array of length at least `n`.
- **`take-index`**: Lookup in `take` is lookup in the original via `fin-inc_<=`.

#### Decidable (In)equality

- **`array/=`**: From `l /= l'` over a `DecSet`, produce a witness index where they differ.

#### Indexing With Index

- **`indexed`**: Pair each element with its index (dependent).
- **`indexed'`**: Same, returning a non-dependent array of pairs.

#### List Conversion

- **`toList`**: Convert an `Array` to a `Data.List` `List`.
- **`toList_length`**: `length (toList l) = l.len`.
- **`fromList`**: Convert a `List` to a length-indexed `Array`.
- **`fromList_toList`**: Round-trip identity `fromList ∘ toList = id`.
