package org.dreamhorizon.pulseserver.service.alert.v4.util;

import java.util.Map;
import java.util.Stack;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExpressionEvaluator {

  public static boolean evaluate(String expression, Map<String, Boolean> variableValues) {
    if (expression == null || expression.trim().isEmpty()) {
      log.warn("Expression is null or empty");
      return false;
    }

    expression = expression.replaceAll("\\s+", "");
    return evaluateExpression(expression, variableValues);
  }

  private static boolean evaluateExpression(String expr, Map<String, Boolean> vars) {
    Stack<Boolean> values = new Stack<>();
    Stack<Character> ops = new Stack<>();

    for (int i = 0; i < expr.length(); i++) {
      char c = expr.charAt(i);

      if (c == ' ') {
        continue;
      }

      // variable / identifier (A, B, C_1, etc.)
      if (Character.isLetterOrDigit(c) || c == '_') {
        StringBuilder sb = new StringBuilder();
        while (i < expr.length()
            && (Character.isLetterOrDigit(expr.charAt(i)) || expr.charAt(i) == '_')) {
          sb.append(expr.charAt(i));
          i++;
        }
        i--;

        String var = sb.toString();
        Boolean value = vars.get(var);
        if (value == null) {
          log.warn("Variable {} not found in variable values", var);
          value = false;
        }
        values.push(value);

      } else if (c == '(') {
        ops.push(c);

      } else if (c == ')') {
        while (!ops.isEmpty() && ops.peek() != '(') {
          values.push(applyOp(ops.pop(), values.pop(), values.pop()));
        }
        if (!ops.isEmpty()) {
          ops.pop(); // pop '('
        }

      } else if (c == '&' && i + 1 < expr.length() && expr.charAt(i + 1) == '&') {
        while (!ops.isEmpty() && hasPrecedence('&', ops.peek())) {
          values.push(applyOp(ops.pop(), values.pop(), values.pop()));
        }
        ops.push('&');
        i++; // skip second '&'

      } else if (c == '|' && i + 1 < expr.length() && expr.charAt(i + 1) == '|') {
        while (!ops.isEmpty() && hasPrecedence('|', ops.peek())) {
          values.push(applyOp(ops.pop(), values.pop(), values.pop()));
        }
        ops.push('|');
        i++; // skip second '|'

      } else if (c == '!') {
        ops.push('!');
      }
    }

    while (!ops.isEmpty()) {
      if (ops.peek() == '!') {
        values.push(applyOp(ops.pop(), values.pop(), null));
      } else {
        values.push(applyOp(ops.pop(), values.pop(), values.pop()));
      }
    }

    return values.isEmpty() ? false : values.pop();
  }

  private static boolean hasPrecedence(char op1, char op2) {
    if (op2 == '(' || op2 == ')') {
      return false;
    }
    if (op1 == '!' && (op2 == '&' || op2 == '|')) {
      return false;
    }
    if ((op1 == '&' || op1 == '|') && op2 == '!') {
      return true;
    }
    return false;
  }

  private static boolean applyOp(char op, Boolean b, Boolean a) {
    if (op == '!') {
      return !b;
    }
    if (a == null) {
      return b != null ? b : false;
    }
    if (b == null) {
      return a;
    }

    return switch (op) {
      case '&' -> a && b;
      case '|' -> a || b;
      default -> false;
    };
  }
}