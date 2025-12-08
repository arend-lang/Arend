package org.arend.core.expr.type;

import org.arend.core.expr.Expression;
import org.arend.core.sort.SortExpression;

public record TypeExpression(Expression expression, SortExpression sort) {}
