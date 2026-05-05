### Data.Array

This module provides operations, transformations, and lemmas for `Array` (length-indexed sequences).

#### Array Construction and Destructors

- **`mkArray`**: Wraps a function `Fin n -> A` into an `Array A n`.
- **`arrayExt`**: Extensionality for arrays: pointwise equality implies array equality.
- **`tail`**: Returns the tail of an array (drops the first element); `nil` for empty.
- **`taild`**: Tail for dependent arrays (`DArray`).
- **`array-unext`**: Extracts pointwise equality from an array equality proof.
- **`len=0`**: If `l.len = 0`, then `l = nil`.
- **`unhead`**: From `a :: l = a' :: l'`, extracts `a = a'`.
- **`untail`**: From `a :: l = a' :: l'`, extracts `l = l'`.

#### Mapping

- **`map`**: Applies `f : A -> B` to each element of an array.
- **`map_::`**: `map f (a :: l) = f a :: map f l`.
- **`cong_::`**: Congruence for `::`: `a = a'` and `l = l'` imply `a :: l = a' :: l'`.

#### Concatenation (`++'` and `++`)

- **`++'` (length-indexed)**: Concatenation preserving the sum of lengths in the type.
  - **`++'_index-left`**: Indexing into the left part of `l ++' m`.
  - **`++'_index-right`**: Indexing into the right part of `l ++' m`.
  - **`split-index`**: Every index into `l ++' m` comes from either the left or right part.
- **`++'-split`**: Any array of length `n + m` splits into a concatenation of two parts.
- **`map_++'`**: `map f (l ++' l') = map f l ++' map f l'`.
- **`map2_++'`**: Pointwise application distributes over `++'`.
- **`++` (length-erased)**: Concatenation for arrays with erased lengths.
  - **`index-left`** / **`++_index-left`**: Index embedding and indexing for the left part.
  - **`index-right`** / **`++_index-right`**: Index embedding and indexing for the right part.
  - **`index-left-nat`** / **`index-right-nat`**: The index embeddings preserve `Nat` values.
  - **`index-left-inj`** / **`index-right-inj`**: The index embeddings are injective.
  - **`index-left/=right`**: Left and right index embeddings produce distinct indices.
  - **`split-index`**: Every index into `l ++ m` comes from either the left or right part.
  - **`++-all`**: If a property holds for all elements of `l` and `l'`, it holds for `l ++ l'`.
  - **`index-big`** / **`Big++-index`**: Indexing into `Big ++ nil ls` via a pair `(i, j)`.
- **`++_++'`**: `l ++ l' = l ++' l'`.
- **`map_++`**: `map f (l ++ l') = map f l ++ map f l'`.
- **`++_nil`**: `l ++ nil = l`.
- **`len_++`**: `(l ++ l').len = l.len + l'.len`.
- **`++-cancel-left`**: `l ++ l1 = l ++ l2` implies `l1 = l2`.
- **`++-cancel-right`**: `l1 ++ l = l2 ++ l` implies `l1 = l2`.
- **`++-assoc`**: `(xs ++ ys) ++ zs = xs ++ (ys ++ zs)`.
- **`replicate_+`**: `replicate (n + m) a = replicate n a ++ replicate m a`.

#### Index Type and Membership

- **`Index`**: `Index x l` is a pair of an index `i` and a proof `l i = x`.
- **`index-left`** (for `Index`): Embeds `Index x l` into `Index x (l ++ l')`.
- **`index-right`** (for `Index`): Embeds `Index x l'` into `Index x (l ++ l')`.
- **`index-dec`**: Decidable membership: given a `DecSet`, decides whether `a` occurs in `l`.

#### Filtering

- **`filter`**: Filters an array by a boolean predicate.
- **`filter-sat`**: Every element of `filter p l` satisfies `p`.
- **`filter_true`**: If all elements satisfy `p`, then `filter p l = l`.
- **`filter_false`**: If no elements satisfy `p`, then `filter p l = nil`.
- **`filter-index`**: If `p (l i) = true`, then `l i` appears in `filter p l`.

#### Big Fold

- **`Big`**: Right fold: `Big op b (a :: l) = op a (Big op b l)`.
- **`Big++_map`**: `Big ++ nil (map (map f) ls) = map f (Big ++ nil ls)`.
- **`Big++-split`**: `l = Big ++ nil (map (:: nil) l)`.

#### FilterMap

- **`filterMap`**: Maps with `A -> Maybe B`, keeping only `just` results.
- **`filterMap-index`**: If `f (l j) = just b`, then `b` appears in `filterMap f l`.

#### Indexed

- **`indexed`**: Pairs each element with its index (dependent array version).
- **`indexed'`**: Pairs each element with its index (non-dependent array version).

#### Replicate

- **`replicate`**: Creates an array of `n` copies of `a`.

#### Keep and Remove

- **`keep`**: Keeps elements satisfying a decidable predicate `P`.
  - **`satisfies`**: Every element of `keep D l` satisfies `P`.
  - **`element`**: If `P (l j)`, then `l j` appears in `keep D l`.
  - **`preimage`**: Every element of `keep D l` comes from `l`.
  - **`no-repeats`**: If `l` is injective, so is `keep D l`.
- **`keep_++`**: `keep D (l ++ l') = keep D l ++ keep D l'`.
- **`keep-all`**: If all elements satisfy `P`, then `keep D l = l`.
- **`keep-none`**: If no elements satisfy `P`, then `keep D l = nil`.
- **`keep-unique`**: If exactly one element satisfies `P`, `keep` returns a singleton.
- **`remove`**: Removes elements satisfying a decidable predicate `P`.
  - **`no-element`**: No element of `remove D l` satisfies `P`.
  - **`element`**: If `¬ P (l j)`, then `l j` appears in `remove D l`.
  - **`preimage`**: Every element of `remove D l` comes from `l`.
  - **`no-repeats`**: If `l` is injective, so is `remove D l`.
  - **`equals`**: If `P` and `Q` are equivalent, `remove DP l = remove DQ l`.
- **`remove-none`**: If no elements satisfy `P`, then `remove D l = l`.
- **`remove_map`**: `remove D (map f l) = map f (remove (D ∘ f) l)`.
- **`keep=remove`**: `keep D l = remove (NotDec ∘ D) l`.
- **`remove=keep`**: `remove D l = keep (NotDec ∘ D) l`.
- **`remove_remove`**: Double removal equals removal by disjunction.
- **`remove-swap`**: Order of two removals can be swapped.
- **`keep_remove=keep`**: `keep DP (remove DQ l) = keep DP l` when `P` and `Q` are disjoint.
- **`remove<=`**: `(remove D l).len <= l.len`.
- **`count_remove_yes`** / **`count_remove_no`** / **`count_remove`**: Count lemmas for `remove`.

#### RemoveElem, Remove1, Nub

- **`removeElem`**: Removes all occurrences of a specific element (via `DecSet`).
- **`remove1`**: Removes the first occurrence of an element from a `suc n`-length array, returning an `n`-length array.
- **`remove1-surj`**: Every index of `remove1 a l` maps to some index of `l`.
- **`remove1/=`**: Elements of `remove1 a l` are not equal to `a` (when `l` is injective).
- **`remove1-inj`**: `remove1 a l` is injective when `l` is injective.
- **`nub`**: Removes duplicates from an array.
- **`nub-isSurj`**: Every element of `l` appears in `nub l`.
- **`nub-preimage`**: Every element of `nub l` comes from `l`.
- **`nub-isInj`**: `nub l` is injective (no duplicates).
- **`nub_id`**: If `l` is already injective, `nub l = l`.

#### Insert

- **`insert`**: Inserts element `a` at position `j` in an array.
- **`insert_zro`**: `insert a l 0 = a :: l`.
- **`insert-index`**: `insert a l j` at index `j` equals `a`.
- **`insert_skip`**: `insert a (skip l k) k = replace l k a`.
- **`skip_insert_=`**: `skip (insert a l j) j = l`.
- **`map_insert`**: `map f (insert a l j) = insert (f a) (map f l) j`.

#### Skip (Element Removal by Index)

- **`skip`**: Removes the element at index `k` from a `suc n`-length array.
  - **`newIndex`**: Reindexes `i` after skipping `j` (for `i ≠ j`).
  - **`newIndex_<`**: `newIndex` preserves strict order.
- **`skip_++'`**: `skip` distributes over `++'` on the left part.
- **`skipExt`**: Extensionality: if arrays agree away from `k`, their skips agree.
- **`skip_0`**: `skip l 0` at `j` equals `l (suc j)`.
- **`skip_map`**: `map f (skip l k) = skip (map f l) k`.
- **`skip_replicate`**: `skip (replicate (suc n) a) k = replicate n a`.

#### Replace

- **`replace`**: Replaces the element at index `i` with `a`.
- **`replace-index`**: `replace l i a` at `i` equals `a`.
- **`replace-notIndex`**: `replace l i a` at `j ≠ i` equals `l j`.
- **`replace_insert`**: `replace (insert a l i) i b = insert b l i`.
- **`skip_replace_=`**: `skip (replace l i a) i = skip l i`.
- **`skip_replace_/=`**: `skip (replace l j a) i = replace (skip l i) (newIndex ...) a` when `j ≠ i`.
- **`skip-index`**: `skip l i` at `newIndex p` equals `l j`.
- **`map_replace`**: `map f (replace l i a) = replace (map f l) i (f a)`.

#### Count

- **`count`**: Counts occurrences of `a` in `l` (requires `DecSet`).
- **`count<=`**: `count l a <= l.len`.
- **`count_++`**: `count (l ++ l') a = count l a + count l' a`.
- **`count_Big++`**: Count distributes over `Big ++`.
- **`count-all`**: If all elements equal `a`, then `count l a = l.len`.
- **`count-none`**: If no elements equal `a`, then `count l a = 0`.
- **`keep_count`**: `keep (decideEq a) l = replicate (count l a) a`.

#### Find and Decidability

- **`find`**: Finds the first element satisfying a decidable predicate, or proves none exists.
- **`index-dec`**: Decidable membership for `DecSet`.
- **`repeats-dec`**: Decides whether an array has repeated elements, returning either a witness pair or an injectivity proof.

#### Forall

- **`forall`**: Boolean "for all" check: `forall p l` returns `true` iff `p` holds for every element.
- **`forall-char`**: If `forall p l = true`, then `p (l j) = true` for all `j`.

#### Fit

- **`fit`**: Adjusts an array to length `n`, padding with `a` if too short, truncating if too long.
- **`fit_<`**: For indices within the original length, `fit` preserves values.
- **`fit_>=`**: For indices beyond the original length, `fit` returns the padding value.
- **`fit_<'`**: Variant of `fit_<` with different index direction.
- **`fit_map`**: `map f (fit a l) = fit (f a) (map f l)`.

#### Array Inequality

- **`array/=`**: If two same-length arrays over a `DecSet` are unequal, there exists a differing index.

#### List Conversion

- **`toList`**: Converts an `Array` to a `List`.
- **`toList_length`**: `length (toList l) = l.len`.
- **`fromList`**: Converts a `List` to an `Array`.
- **`fromList_toList`**: `fromList (toList l) = l`.

#### Take

- **`take`**: Takes the first `n` elements of an array (requires `n <= l.len`).
- **`take-index`**: `take n l p` at `j` equals `l (fin-inc_<= p j)`.

#### SingleAt

- **`singleAt`**: Creates an array that is `value` at index `j` and `def` everywhere else.
  - **`alt`**: Alternative recursive definition of `singleAt`.
  - **`char`**: `singleAt j value def = alt j value def`.
