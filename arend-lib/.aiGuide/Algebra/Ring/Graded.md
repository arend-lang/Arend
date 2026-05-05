### Algebra.Ring.Graded

Graded commutative rings: a `CRing` equipped with a homogeneity predicate decomposing every element into homogeneous components.

#### Main Class

- **`GradedCRing`**: Extends `CRing` with a graded structure. Provides a predicate `isHomogen : E -> Nat -> \Prop` identifying homogeneous elements of each degree, together with axioms: zero is homogeneous of any degree, `-1` is homogeneous of degree 0, sums of same-degree homogeneous elements stay homogeneous, products of homogeneous elements add degrees, every element decomposes as a finite sum of degree-`n` homogeneous parts (`homogen-decomp`), and such a decomposition summing to zero forces each component to be zero (`homogen-unique`).

#### Class Fields

- **`isHomogen`**: Predicate `E -> Nat -> \Prop` asserting that an element is homogeneous of a given degree.
- **`homogen-zro`**: `0` is homogeneous of every degree `n`.
- **`homogen-negative_ide`**: `-1` is homogeneous of degree 0.
- **`homogen-+`**: Sum of two degree-`n` homogeneous elements is homogeneous of degree `n`.
- **`homogen-*`**: Product of degree-`n` and degree-`m` homogeneous elements is homogeneous of degree `n + m`.
- **`homogen-decomp`**: Every `x : E` admits an array `l` whose `n`-th entry is homogeneous of degree `n` and whose big sum equals `x`.
- **`homogen-unique`**: If a degree-respecting array sums to zero, then every entry is zero (uniqueness of homogeneous decomposition).

#### Auxiliary Definitions (in `\where`)

- **`Similar`**: Inductive relation on arrays over an `AddPointed` expressing pointwise equality up to padding with zeros at either end. Constructors handle empty/empty, empty/cons (head must be 0), cons/empty, and cons/cons (heads equal) cases recursively.
- **`similar-nil`**: If `Similar l nil`, then every entry of `l` is zero.
- **`similar-eq`**: For `Similar l l'`, each index `j` of `l` either matches an index `i` of `l'` with equal value and `i = j`, or the entry `l j` is zero.
