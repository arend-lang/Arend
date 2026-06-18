### Algebra.Ring.Graded

Graded commutative rings: rings whose elements decompose into homogeneous components indexed by degree.

A `GradedCRing` extends `CRing` with a homogeneity predicate `isHomogen : E -> Nat -> \Prop` that is closed under zero, addition (within a fixed degree), multiplication (degrees add), and contains `-1` in degree 0. The defining axioms require every element to admit a decomposition as a finite sum `BigSum l` where `l n` is homogeneous of degree `n`, and that such decompositions are unique up to vanishing components (`homogen-unique`). Uniqueness is expressed via the auxiliary `Similar` relation on arrays — a componentwise equality-or-zero relation that absorbs trailing zeros — which lets the development reason about decompositions of different lengths uniformly. From these axioms the file derives degree uniqueness, splitting lemmas for sums and products, and factorization lemmas used to lift homogeneous structure through ring operations.

#### Main Class

- **`GradedCRing`**: Extends `CRing` with the predicate `isHomogen` and axioms `homogen-zro`, `homogen-negative_ide`, `homogen-+`, `homogen-*`, `homogen-decomp` (existence of a homogeneous decomposition), and `homogen-unique` (a homogeneous array summing to zero is pointwise zero).
- **`isHomogenArray`**: Predicate that an array `l` has `l n` homogeneous of degree `n` at each index.

#### Basic Closure Properties

- **`homogen-ide`**: `1` is homogeneous of degree 0.
- **`homogen-negative`**: Negation preserves homogeneity at each degree.
- **`homogen-pow`**: `pow a m` is homogeneous of degree `n * m` when `a` has degree `n`.
- **`homogen-BigSum`**: Sum of elements all homogeneous of the same degree `n` is homogeneous of degree `n`.
- **`homogen-FinSum`**: Same statement for `FinSum` indexed by a `FinSet`.

#### Uniqueness of Decompositions

- **`homogen-similar`**: Two homogeneous arrays with equal sums are `Similar` — the key strengthening of `homogen-unique` to comparison of two decompositions. Inner helpers `diff`, `diff=0->similar`, `diff-sum`, `diff-homogen` build a difference array whose vanishing yields similarity.
- **`degree-unique`**: A nonzero element cannot be homogeneous of two distinct degrees: returns `Or (a = 0) (n = m)`. Uses helpers `array2` (a length-`(suc n + suc m)` array placing `a` at index `n` and `-a` at index `m`) and `sum-lem1`/`sum-lem2` (sums of arrays supported on one or two indices).
- **`degree-unique2`**: If `a = b + c` with `a, b, c` homogeneous of degrees `n, m, k`, then either `b = 0`, `c = 0`, or `m = k`. Helper `similar-unique` extracts vanishing entries from a similarity to a single-element decomposition.
- **`homogenSum-unique`**: For a homogeneous array `l` whose sum is itself homogeneous of degree `n`, either some entry `l j` realizes the sum with `j = n`, or the sum is zero.

#### Concatenation and Factorization

- **`homogen-++`**: Concatenation of homogeneous arrays remains homogeneous when the second array's degrees start at `l.len`. Helper `aux` generalizes the starting offset.
- **`homogen-factor`**: If `b = a * c` with `b, a` homogeneous, there exists a homogeneous `c'` with `b = a * c'`. Helper `aux` performs induction over a `Similar` relation between `b` placed at one index and `a *` applied to a homogeneous decomposition.
- **`homogen-factor2`**: Two-factor variant: if `b = a1 * c + a2 * d` with `b, a1, a2` homogeneous, there exist homogeneous `c', d'` with `b = a1 * c' + a2 * d'`. Inner `sum`, `sum_nil`, `BigSum_sum`, `homogen-sum` define a pointwise sum on (possibly unequal-length) arrays preserving homogeneity, and `aux` performs the analogous similarity induction over two factor branches.

#### Reduction to Homogeneous Sums

- **`FinSum-homogen`**: A `FinSum` of elements each homogeneous of some degree equals `BigSum` of an array indexed by `0 .. FinJoin (degrees)` where each entry sums all elements of the matching degree. Helpers `array` and `array-homogen` build this array and prove it is homogeneous.
- **`sum-decomp`**: For an array of pairs `(s, t)` with each `t` homogeneous of some degree, produces a refined decomposition expressing `BigSum (s_i * t_i)` as `BigSum_n FinSum_j (g_j * t_{h j})` where each `g_j * t_{h j}` is homogeneous of degree `n` — the canonical bigraded regrouping used to lift relations between elements to homogeneous relations.

#### Similarity of Arrays (in `\where`)

- **`Similar`**: Inductive relation `Similar l l'` on `Array A` for an `AddPointed` `A`, with constructors `nil-nil-similar`, `nil-::-similar` (`a = 0` and tail similar), `::-nil-similar`, and `::-::-similar` (`a = a'` and tails similar). Captures equality up to padding by zeros at either end.
- **`similar-nil`**: If `l` is similar to `nil`, every entry of `l` is zero.
- **`similar-eq`**: For `Similar l l'`, every index `j` of `l` either matches an index `i` of `l'` with `l j = l' i` and `i = j` as naturals, or `l j = 0`.
