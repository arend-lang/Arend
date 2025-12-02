package org.arend.typechecking.implicitargs.equations;

import org.arend.core.sort.Level;
import org.arend.term.concrete.Concrete;

public record DeferredLevelEquation(Level level1, Level level2, Concrete.SourceNode sourceNode) {}
