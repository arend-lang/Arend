package org.arend.typechecking.implicitargs.equations;

import org.arend.ext.core.ops.CMP;
import org.arend.term.concrete.Concrete;

public record AbstractEquation<T>(T left, T right, CMP cmp, Concrete.SourceNode sourceNode) {}
