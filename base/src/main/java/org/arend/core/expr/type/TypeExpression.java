package org.arend.core.expr.type;

import org.arend.core.expr.Expression;
import org.arend.core.sort.Sort;

public record TypeExpression(Expression expression, Sort sort) {}
