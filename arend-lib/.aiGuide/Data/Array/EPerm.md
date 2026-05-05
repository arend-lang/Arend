### Data.Array.EPerm

This module defines extensional permutations (`EPerm`) for arrays of possibly different lengths, along with related operations and decision procedures.

#### EPerm Type

- **`EPerm`**: Inductive relation on arrays `l1` and `l2` (possibly different lengths) with constructors `eperm-nil`, `eperm-:: (a = b) (EPerm l1 l2)`, `eperm-swap`, and `eperm-trans`.

#### EPerm Utilities

- **`eperm-refl`**: Reflexivity of `EPerm`.
- **`eperm-=`**: Equality implies `EPerm`.
- **`eperm-sym`**: Symmetry of `EPerm`.
- **`eperm-++-comm`**: `EPerm (l ++ l') (l' ++ l)`.
- **`eperm-++-left`**: `EPerm l1 l2` implies `EPerm (l1 ++ l) (l2 ++ l)`.
- **`eperm-++-right`**: `EPerm l1 l2` implies `EPerm (l ++ l1) (l ++ l2)`.
- **`eperm-++`**: Combines two `EPerm` proofs over concatenation.
- **`eperm-swap-trans`**: Swap followed by transition.
- **`eperm-swap-tail`**: Swap head elements with `EPerm` on tails.
- **`EPerm_++-swap`**: `EPerm (l ++ a :: l') (a :: l ++ l')`.

#### Functorial Operations

- **`EPerm_map`**: `EPerm` is preserved by `map f`.
  - **`conv`**: Converse for injective `f` on `\Set` types.
- **`EPerm_keep`**: `EPerm` is preserved by `keep D`.
- **`EPerm_remove`**: `EPerm` is preserved by `remove D`.
- **`EPerm_nub`**: `EPerm` is preserved by `nub`.
- **`EPerm_filter`**: `EPerm` is preserved by `filter f`.

#### Removal and Counting

- **`eperm-remove`**: For injective arrays, `EPerm l (l j :: removeElem (l j) l)`.
- **`EPerm_len`**: `EPerm l l'` implies `l.len = l'.len`.
- **`EPerm_count`**: `EPerm l l'` implies `count l a = count l' a`.
- **`count_EPerm`**: Converse: equal counts for all elements implies `EPerm`.

#### Decidability and Equivalence

- **`EPermDec`**: Decidable `EPerm` for `DecSet` arrays.
- **`EPerm-equivalence`**: `EPerm` (truncated) forms an `Equivalence` on `Array A`.

#### Conversion to/from Perm

- **`Perm_EPerm`**: Fixed-length `Perm` implies `EPerm`.
- **`EPerm_Perm`**: `EPerm` between same-length arrays implies `Perm`.
- **`EPerm_Perm_transport`**: `EPerm` with length proof yields `Perm` via transport.

#### Equivalence Characterization

- **`eperm_equiv`**: `EPerm l l'` yields an `Equiv` on indices with matching elements.
  - **`conv`**: Converse: such an equivalence implies `EPerm`.

#### Keep/Remove Split

- **`keep_remove-split`**: `EPerm l (keep D l ++ remove D l)`.
