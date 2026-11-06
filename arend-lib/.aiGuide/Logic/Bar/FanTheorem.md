### Logic.Bar.FanTheorem

The fan theorem for inductive bars, in plain and indexed forms.

The module proves the constructive fan theorem in the form given by Fridlender (An Interpretation of the Fan Theorem in Type Theory, 1998): if a monotone predicate `P` is a bar (`Bar P nil`) on arrays over `A`, then a bar already exists for the predicate that `P` holds for all paths through a finitely branching sequence of arrays (`fanTheorem.AllPaths`). The indexed version (`indexedFanTheorem`) strengthens it to a statement about a position `i` where all branches `j` at `i` are covered by paths witnessing a predicate `Q`; its proof uses a pigeonhole argument. The bars are those of `Logic.Bar`.

#### Fan theorem

- **`fanTheorem`**: If `P` is `IsBarMonotone` and `Bar P nil`, then `Bar (AllPaths P) nil`, where `fanTheorem.AllPaths P ls` says that `P` holds for every path through the sequence of arrays `ls` (a path picks one element `a_i` from each `l_i`).

#### Indexed fan theorem

- **`indexedFanTheorem`**: For monotone `P` with `Bar P nil`, and `Q l i` such that every `l` with `P l` has some `i : Fin l.len` with `Q l i`, there is a bar for `indexedFanTheorem.IsFan` over `nil`. `indexedFanTheorem.IsFan ls` says that some position `i` of `ls` has the property that every `j` there is the value at `i` of a path `js` through `ls` with `Q (walk js) i`.
