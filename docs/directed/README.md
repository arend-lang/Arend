# Directed type theory

**Consistency status:** this snapshot is inconsistent. `inconsistency.ard`
defines a closed term of an empty datatype, accepted by both the typechecker
and its core double checker. `DI` is inferred to be in `Set0`, so the
groupoidal `idd` eliminator applies to its canonical directed segment;
invariant endpoint matching then separates its endpoints. Appendix C of
the document explains the contradiction. The passing tests below establish
only regression-test success, not consistency.

- `directed-theory.tex`: self-contained LaTeX source.
- `directed-theory.pdf`: compiled presentation of the rules.
- `inconsistency.ard`: prelude-only counterexample for the inspected snapshot.

The presentation follows the style of section 2.1 of [Theorem prover
Arend](https://arend-lang.github.io/assets/lang-paper.pdf). Its directed rules
are extracted from repository commit
`a7a01117506d7c81c7d38b78feec5204af7ec949`, especially `lib/Prelude.ard`, the
Java typechecker and normalizer, and the directed-theory tests.

It covers variance, categorical universes, covariant functions and sums,
`DI` and `DPath`, `fill2`, the complete five-face signature of `fill3`,
`coe+`, `iso+`, `iso+_coe+`, `rezk`, and the restriction of `idd`
elimination to groupoidal types. Primitive judgmental equations are
distinguished from derived categorical laws. Appendix A maps the rules to
source files and records the path-universe qualification; Appendix B gives
seven prelude-only examples.

## Build

From the repository root, with a standard TeX Live installation:

```sh
mkdir -p /tmp/arend-directed-tex
pdflatex -interaction=nonstopmode -halt-on-error -output-directory=/tmp/arend-directed-tex docs/directed/directed-theory.tex
pdflatex -interaction=nonstopmode -halt-on-error -output-directory=/tmp/arend-directed-tex docs/directed/directed-theory.tex
cp /tmp/arend-directed-tex/directed-theory.pdf docs/directed/directed-theory.pdf
```

No external images, bibliography files, or shell escape are required.

## Validation

The root directed-theory regression suite passed: 234 tests, no failures or
errors. Run it with the explicit root task (the leading colon avoids
selecting the IntelliJ plugin's separate test task):

```sh
./gradlew --offline :test --tests 'org.arend.cat.*' --tests 'org.arend.typechecking.patternmatching.IddTest'
```

All seven examples were extracted directly from the LaTeX listing and
checked together using the repository's `TypeCheckingTestCase`, including
its core double checker. Three additional temporary negative probes
confirmed the absence of constant-family reduction for `coe+` (at a
variable and at `dright`) and of a special identity reduction for `rezk`.
These checks used classes rebuilt from this snapshot, not the older
standalone CLI jar at the repository root.

The PDF was compiled with resolved references and no overfull/underfull
box warnings, then rendered and visually reviewed page by page.
