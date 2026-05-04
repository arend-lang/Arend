### Data Directory Overview

This directory provides core data types, collections, and their properties.

#### Core Data Types

- **`Bool.md`**: `Bool` type with `false`/`true`, `So` (boolean-to-proposition), boolean operations (`not`, `and`, `or`, `xor`, `if`), and associated lemmas (`not-isInv`, `true/=false`, `toSigma`/`fromSigma`, `toOr`/`fromOr`).
- **`Maybe.md`**: `Maybe` type with `nothing`/`just`, `map`, `maybe` eliminator, `unjust` extractor, and `just-injective`.
- **`Or.md`**: `Or` (coproduct) type with `inl`/`inr`, `levelProp`, `map`, `rec` eliminator, and `Or_Equiv`.
- **`Sigma.md`**: Tuple mapping utilities (`tupleMap`, `tupleMapLeft`, `tupleMapRight` with projection lemmas) and `unit-isContr` (contractibility of `\Sigma`).

#### Finite Types

- **`Fin.md`**: `Fin` utilities — constructors (`fzero`, `fsuc`), destructors (`fpred`, `fpredP`, `fcase`), equality/inequality lemmas (`unfsuc`, `fsuc/=`, `fsuc/=0`, `nat_fin_=`, `fin_nat_/=`), predecessor round-trips, and `finLast`.

#### Collections

- **`Array.md`** / **`Array/`**: Length-indexed arrays — construction (`mkArray`, `arrayExt`), mapping (`map`), concatenation (`++'`, `++` with index embeddings and splitting), filtering (`filter`, `keep`, `remove`), `Big` fold, `filterMap`, `insert`/`skip`/`replace`, `count`, `find`, `nub`, `replicate`, `forall`, `fit`, `singleAt`, list conversion, `take`, and many associated lemmas. Subdirectory contains `EPerm.md` (extensional permutations), `Pairs.md` (Cartesian product combinations), `Perm.md` (fixed-length permutations with sign), `Sort.md` (sorting with correctness), `Split.md` (`nub-split`).
- **`List.md`**: Linked lists — `List` type, `length`, `!!`, `headDef`, `tail`, `++`, `replicate`, `map`, `ListMonoid`, `splitAt`/`take`/`drop`, `replace`/`slice`, predicates (`All`, `All2`, `AllC`), `count`, `group`, sorting (`Sort` module with `Perm`, `Sorted`, insertion sort, red-black tree sort), membership (`contains`, `InList`, `~`), and set operations (`union`).
- **`SubList.md`**: `SubList` inductive relation with constructors, composition (transitivity), contractibility/impossibility lemmas, invariance lemmas, and transport lemmas.

#### Homotopy / Colimits

- **`SeqColimit`**: Sequential colimits — `Seq` class (sequential diagram), `SeqColimit` HIT with `inSC`/`quotSC`, `flattening` (descent/total space equivalence), `seqColimit-surj` (surjectivity from level 0), and `constantMaps` (contractibility for constant sequences).
