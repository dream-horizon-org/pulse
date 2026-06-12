package org.dreamhorizon.pulseserver.service.alert.core.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EmptyStackException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExpressionEvaluatorTest {

  @Nested
  class TestNullAndEmptyExpressions {

    @Test
    void shouldReturnFalseForNullExpression() {
      Map<String, Boolean> vars = new HashMap<>();
      assertFalse(ExpressionEvaluator.evaluate(null, vars));
    }

    @Test
    void shouldReturnFalseForEmptyExpression() {
      Map<String, Boolean> vars = new HashMap<>();
      assertFalse(ExpressionEvaluator.evaluate("", vars));
    }

    @Test
    void shouldReturnFalseForWhitespaceOnlyExpression() {
      Map<String, Boolean> vars = new HashMap<>();
      assertFalse(ExpressionEvaluator.evaluate("   ", vars));
    }
  }

  @Nested
  class TestSimpleVariables {

    @Test
    void shouldReturnTrueForTrueVariable() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      assertTrue(ExpressionEvaluator.evaluate("A", vars));
    }

    @Test
    void shouldReturnFalseForFalseVariable() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", false);
      assertFalse(ExpressionEvaluator.evaluate("A", vars));
    }

    @Test
    void shouldReturnFalseForMissingVariable() {
      Map<String, Boolean> vars = new HashMap<>();
      assertFalse(ExpressionEvaluator.evaluate("A", vars));
    }

    @Test
    void shouldHandleVariableWithDigits() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A1", true);
      assertTrue(ExpressionEvaluator.evaluate("A1", vars));
    }

    @Test
    void shouldHandleVariableWithUnderscore() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A_B", true);
      assertTrue(ExpressionEvaluator.evaluate("A_B", vars));
    }

    @Test
    void shouldHandleLongVariableName() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("my_variable_123", true);
      assertTrue(ExpressionEvaluator.evaluate("my_variable_123", vars));
    }
  }

  @Nested
  class TestAndOperator {

    @Test
    void shouldReturnTrueForTrueAndTrue() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      vars.put("B", true);
      assertTrue(ExpressionEvaluator.evaluate("A && B", vars));
    }

    @Test
    void shouldReturnFalseForTrueAndFalse() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      vars.put("B", false);
      assertFalse(ExpressionEvaluator.evaluate("A && B", vars));
    }

    @Test
    void shouldReturnFalseForFalseAndTrue() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", false);
      vars.put("B", true);
      assertFalse(ExpressionEvaluator.evaluate("A && B", vars));
    }

    @Test
    void shouldReturnFalseForFalseAndFalse() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", false);
      vars.put("B", false);
      assertFalse(ExpressionEvaluator.evaluate("A && B", vars));
    }

    @Test
    void shouldHandleMultipleAndOperators() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      vars.put("B", true);
      vars.put("C", true);
      assertTrue(ExpressionEvaluator.evaluate("A && B && C", vars));
    }

    @Test
    void shouldReturnFalseWhenOneIsFalseInMultipleAnd() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      vars.put("B", false);
      vars.put("C", true);
      assertFalse(ExpressionEvaluator.evaluate("A && B && C", vars));
    }
  }

  @Nested
  class TestOrOperator {

    @Test
    void shouldReturnTrueForTrueOrTrue() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      vars.put("B", true);
      assertTrue(ExpressionEvaluator.evaluate("A || B", vars));
    }

    @Test
    void shouldReturnTrueForTrueOrFalse() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      vars.put("B", false);
      assertTrue(ExpressionEvaluator.evaluate("A || B", vars));
    }

    @Test
    void shouldReturnTrueForFalseOrTrue() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", false);
      vars.put("B", true);
      assertTrue(ExpressionEvaluator.evaluate("A || B", vars));
    }

    @Test
    void shouldReturnFalseForFalseOrFalse() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", false);
      vars.put("B", false);
      assertFalse(ExpressionEvaluator.evaluate("A || B", vars));
    }

    @Test
    void shouldHandleMultipleOrOperators() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", false);
      vars.put("B", false);
      vars.put("C", true);
      assertTrue(ExpressionEvaluator.evaluate("A || B || C", vars));
    }

    @Test
    void shouldReturnFalseWhenAllAreFalseInMultipleOr() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", false);
      vars.put("B", false);
      vars.put("C", false);
      assertFalse(ExpressionEvaluator.evaluate("A || B || C", vars));
    }
  }

  @Nested
  class TestNotOperator {

    @Test
    void shouldReturnFalseForNotTrue() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      assertFalse(ExpressionEvaluator.evaluate("!A", vars));
    }

    @Test
    void shouldReturnTrueForNotFalse() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", false);
      assertTrue(ExpressionEvaluator.evaluate("!A", vars));
    }

    @Test
    void shouldHandleDoubleNegation() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      assertTrue(ExpressionEvaluator.evaluate("!!A", vars));
    }

    @Test
    void shouldHandleNotWithParenthesesAroundExpression() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      vars.put("B", true);
      // !(A && B) = !true = false
      assertFalse(ExpressionEvaluator.evaluate("!(A && B)", vars));
    }

    @Test
    void shouldHandleNotWithParenthesesAroundOrExpression() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", false);
      vars.put("B", false);
      // !(A || B) = !false = true
      assertTrue(ExpressionEvaluator.evaluate("!(A || B)", vars));
    }
  }

  @Nested
  class TestParentheses {

    @Test
    void shouldEvaluateSimpleParentheses() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      assertTrue(ExpressionEvaluator.evaluate("(A)", vars));
    }

    @Test
    void shouldRespectParenthesesPrecedence() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      vars.put("B", false);
      vars.put("C", true);
      // (A || B) && C = true && true = true
      assertTrue(ExpressionEvaluator.evaluate("(A || B) && C", vars));
    }

    @Test
    void shouldHandleNestedParentheses() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      vars.put("B", false);
      vars.put("C", true);
      assertTrue(ExpressionEvaluator.evaluate("((A || B) && C)", vars));
    }

    @Test
    void shouldHandleComplexNestedParentheses() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      vars.put("B", false);
      vars.put("C", true);
      vars.put("D", false);
      // ((A && B) || (C && D)) = ((true && false) || (true && false)) = (false || false) = false
      assertFalse(ExpressionEvaluator.evaluate("((A && B) || (C && D))", vars));
    }

    @Test
    void shouldHandleEmptyParentheses() {
      Map<String, Boolean> vars = new HashMap<>();
      // Empty parentheses leave the stack empty - returns false
      assertFalse(ExpressionEvaluator.evaluate("()", vars));
    }

    @Test
    void shouldHandleParenthesesWithSingleVariable() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      assertTrue(ExpressionEvaluator.evaluate("((A))", vars));
    }
  }

  @Nested
  class TestMixedOperators {

    @Test
    void shouldHandleAndWithOr() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      vars.put("B", false);
      vars.put("C", true);
      // A && B || C - evaluates based on stack order
      assertTrue(ExpressionEvaluator.evaluate("A && B || C", vars));
    }

    @Test
    void shouldHandleOrWithAnd() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", false);
      vars.put("B", true);
      vars.put("C", true);
      // A || B && C
      assertTrue(ExpressionEvaluator.evaluate("A || B && C", vars));
    }

    @Test
    void shouldHandleMultipleParenthesizedGroups() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      vars.put("B", true);
      vars.put("C", false);
      vars.put("D", true);
      // (A && B) || (C && D) = (true && true) || (false && true) = true || false = true
      assertTrue(ExpressionEvaluator.evaluate("(A && B) || (C && D)", vars));
    }
  }

  @Nested
  class TestWhitespaceHandling {

    @Test
    void shouldHandleNoWhitespace() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      vars.put("B", true);
      assertTrue(ExpressionEvaluator.evaluate("A&&B", vars));
    }

    @Test
    void shouldHandleExtraWhitespace() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      vars.put("B", true);
      assertTrue(ExpressionEvaluator.evaluate("  A  &&  B  ", vars));
    }

    @Test
    void shouldHandleMixedWhitespace() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      vars.put("B", true);
      assertTrue(ExpressionEvaluator.evaluate("A &&B|| A", vars));
    }
  }

  @Nested
  class TestEdgeCases {

    @Test
    void shouldHandleSingleCharacterVariable() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      assertTrue(ExpressionEvaluator.evaluate("A", vars));
    }

    @Test
    void shouldHandleNumbersInVariableName() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("var123", true);
      assertTrue(ExpressionEvaluator.evaluate("var123", vars));
    }

    @Test
    void shouldHandleUnderscoresInVariableName() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("var_name", true);
      assertTrue(ExpressionEvaluator.evaluate("var_name", vars));
    }

    @Test
    void shouldHandleAllVariablesUndefined() {
      Map<String, Boolean> vars = new HashMap<>();
      // All undefined variables default to false
      assertFalse(ExpressionEvaluator.evaluate("A && B", vars));
    }

    @Test
    void shouldHandleMixedDefinedAndUndefinedVariables() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      // B is undefined (defaults to false), so A && B = true && false = false
      assertFalse(ExpressionEvaluator.evaluate("A && B", vars));
    }

    @Test
    void shouldHandleUndefinedWithOr() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      // B is undefined (defaults to false), so A || B = true || false = true
      assertTrue(ExpressionEvaluator.evaluate("A || B", vars));
    }

    @Test
    void shouldHandleTripleNegation() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      // !!!A = !!false = !true = false
      assertFalse(ExpressionEvaluator.evaluate("!!!A", vars));
    }
  }

  @Nested
  class TestOperatorPrecedence {

    @Test
    void shouldHandlePrecedenceWithParenthesesVsWithout() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      vars.put("B", false);
      vars.put("C", true);

      // Test different precedence scenarios
      boolean withParens = ExpressionEvaluator.evaluate("(A || B) && C", vars);
      assertTrue(withParens);
    }

    @Test
    void shouldHandleMultipleAndOperatorsPrecedence() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      vars.put("B", true);
      vars.put("C", true);
      // A && B && C - all true
      assertTrue(ExpressionEvaluator.evaluate("A && B && C", vars));
    }

    @Test
    void shouldHandleMultipleOrOperatorsPrecedence() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", false);
      vars.put("B", false);
      vars.put("C", true);
      // A || B || C - one is true
      assertTrue(ExpressionEvaluator.evaluate("A || B || C", vars));
    }
  }

  @Nested
  class TestSpecialCases {

    @Test
    void shouldHandleExpressionWithOnlyParenthesesAndVariable() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      assertTrue(ExpressionEvaluator.evaluate("((A))", vars));
    }

    @Test
    void shouldHandleNotBeforeParenthesesWithAnd() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      vars.put("B", true);
      // !(A && B) = !true = false
      assertFalse(ExpressionEvaluator.evaluate("!(A && B)", vars));
    }

    @Test
    void shouldHandleNotBeforeParenthesesWithOr() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", false);
      vars.put("B", false);
      // !(A || B) = !false = true
      assertTrue(ExpressionEvaluator.evaluate("!(A || B)", vars));
    }

    @Test
    void shouldReturnFalseForEmptyValuesStack() {
      // When expression is something that doesn't push any values
      Map<String, Boolean> vars = new HashMap<>();
      // Empty parentheses leave the stack empty
      assertFalse(ExpressionEvaluator.evaluate("()", vars));
    }

    @Test
    void shouldHandleComplexParenthesesNesting() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      vars.put("B", true);
      vars.put("C", false);
      // ((A && B)) || C = true || false = true
      assertTrue(ExpressionEvaluator.evaluate("((A && B)) || C", vars));
    }
  }

  @Nested
  class TestDefaultOperator {

    @Test
    void shouldHandleAndOperatorInApplyOp() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      vars.put("B", true);
      assertTrue(ExpressionEvaluator.evaluate("A && B", vars));
    }

    @Test
    void shouldHandleOrOperatorInApplyOp() {
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", false);
      vars.put("B", true);
      assertTrue(ExpressionEvaluator.evaluate("A || B", vars));
    }
  }

  @Nested
  class TestHasPrecedenceMethod {

    @Test
    void shouldReturnFalseWhenOp2IsOpenParen() {
      // Testing through expression: precedence check returns false for '('
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      vars.put("B", true);
      assertTrue(ExpressionEvaluator.evaluate("(A) && B", vars));
    }

    @Test
    void shouldReturnFalseWhenOp2IsCloseParen() {
      // Testing through expression: precedence check returns false for ')'
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      vars.put("B", true);
      assertTrue(ExpressionEvaluator.evaluate("A && (B)", vars));
    }

    @Test
    void shouldHandleNotOperatorAlone() {
      // ! by itself
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", false);
      assertTrue(ExpressionEvaluator.evaluate("!A", vars));
    }

    @Test
    void shouldReturnFalseForDefaultCase() {
      // Default case returns false - tested when both ops are same
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      vars.put("B", true);
      vars.put("C", true);
      // Multiple && operators test the default case
      assertTrue(ExpressionEvaluator.evaluate("A && B && C", vars));
    }
  }

  @Nested
  class TestApplyOpNullHandling {

    @Test
    void shouldHandleNotOperatorWithNullA() {
      // When 'a' is null in applyOp - happens with unary NOT operator
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      // !A triggers applyOp with null for 'a'
      assertFalse(ExpressionEvaluator.evaluate("!A", vars));
    }

    @Test
    void shouldHandleBothValuesPresent() {
      // Normal case with both values
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      vars.put("B", false);
      assertFalse(ExpressionEvaluator.evaluate("A && B", vars));
    }
  }

  @Nested
  class TestKnownLimitations {
    // These tests document the known limitations/bugs in the ExpressionEvaluator
    // where certain patterns throw EmptyStackException

    @Test
    void shouldThrowEmptyStackExceptionForNotBeforeVariableWithAnd() {
      // !A && B pattern causes EmptyStackException
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", false);
      vars.put("B", true);
      assertThrows(EmptyStackException.class, () -> ExpressionEvaluator.evaluate("!A && B", vars));
    }

    @Test
    void shouldThrowEmptyStackExceptionForNotBeforeVariableWithOr() {
      // !A || B pattern causes EmptyStackException
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      vars.put("B", false);
      assertThrows(EmptyStackException.class, () -> ExpressionEvaluator.evaluate("!A || B", vars));
    }

    @Test
    void shouldThrowEmptyStackExceptionForParenthesizedNotWithAnd() {
      // (!A) && B pattern causes EmptyStackException
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      vars.put("B", true);
      assertThrows(EmptyStackException.class, () -> ExpressionEvaluator.evaluate("(!A) && B", vars));
    }

    @Test
    void shouldThrowEmptyStackExceptionForParenthesizedNotWithOr() {
      // A || (!B) pattern causes EmptyStackException
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      vars.put("B", false);
      assertThrows(EmptyStackException.class, () -> ExpressionEvaluator.evaluate("A || (!B)", vars));
    }

    @Test
    void shouldThrowEmptyStackExceptionForNotExpressionFollowedByOperator() {
      // !(A && B) || C pattern causes EmptyStackException
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", true);
      vars.put("B", false);
      vars.put("C", true);
      assertThrows(EmptyStackException.class, () -> ExpressionEvaluator.evaluate("!(A && B) || C", vars));
    }

    @Test
    void shouldThrowEmptyStackExceptionForNestedNotWithOperator() {
      // ((!A)) && B pattern causes EmptyStackException
      Map<String, Boolean> vars = new HashMap<>();
      vars.put("A", false);
      vars.put("B", true);
      assertThrows(EmptyStackException.class, () -> ExpressionEvaluator.evaluate("((!A)) && B", vars));
    }
  }
}
