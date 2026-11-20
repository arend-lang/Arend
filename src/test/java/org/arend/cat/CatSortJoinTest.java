package org.arend.cat;

import org.arend.core.sort.Level;
import org.arend.core.sort.Sort;
import org.arend.core.sort.SortExpression;
import org.arend.ext.core.level.ConstLevel;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * {@link Sort#max} computes sorts of type formers, while {@link Sort#join} is the least upper bound of the subtyping relation,
 * in which \Type u is not a subtype of \Cat v for finite v.
 */
public class CatSortJoinTest {
  private static Sort cat(int pLevel) {
    return new Sort(new Level(BigInteger.valueOf(pLevel)), ConstLevel.CAT_INFINITY);
  }

  @Test
  public void maxTypeCatTest() {
    assertEquals(cat(1), Sort.TypeOfLevel(1).max(cat(0)));
  }

  @Test
  public void subtypingTest() {
    assertFalse(Sort.TypeOfLevel(0).isLessOrEquals(cat(0)));
    assertTrue(Sort.TypeOfLevel(0).isLessOrEqualsNonStrictCat(cat(0)));
    assertTrue(Sort.SetOfLevel(0).isLessOrEquals(cat(0)));
    assertTrue(Sort.PROP.isLessOrEquals(cat(0)));
    assertTrue(Sort.TypeOfLevel(0).isLessOrEquals(Sort.INFINITY));
  }

  @Test
  public void joinTypeCatTest() {
    Sort join = Sort.TypeOfLevel(1).join(cat(0));
    assertTrue(join.isOmega());
    assertTrue(Sort.TypeOfLevel(1).isLessOrEquals(join));
    assertTrue(cat(0).isLessOrEquals(join));
    assertTrue(cat(0).join(Sort.TypeOfLevel(1)).isOmega());
  }

  @Test
  public void joinSetCatTest() {
    assertEquals(cat(1), Sort.SetOfLevel(1).join(cat(0)));
  }

  @Test
  public void joinExpressionTest() {
    SortExpression join = SortExpression.makeJoin(Arrays.asList(new SortExpression.Const(Sort.TypeOfLevel(0)), new SortExpression.Const(cat(0))));
    assertTrue(join instanceof SortExpression.Const(Sort sort) && sort.isOmega());
  }

  @Test
  public void catUniverseTest() {
    assertEquals(Sort.TypeOfLevel(1), cat(0).succ());
    assertEquals(cat(1), Sort.TypeOfLevel(0).succ());
  }
}
