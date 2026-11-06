### Debug

Debugging helpers for the typechecker.

The module currently provides one meta, which runs another meta or expression and reports how long it took.

#### Timing

- **`runTimed`**: A meta that evaluates its argument to normal form and prints the elapsed time (the difference of `time` values) before returning the result.
