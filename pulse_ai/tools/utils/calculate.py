"""Tool: calculate — Safe arithmetic evaluator for the LLM.

The LLM is unreliable at arithmetic (division, percentages, rates).
This tool lets it offload calculations to Python instead of doing
mental math on raw numbers.

Security: Uses AST-based evaluation with a strict allowlist of node
types and functions. No arbitrary code execution is possible.
"""

import ast
import math
import operator
from typing import Any

# Allowed binary operators
_OPERATORS = {
    ast.Add: operator.add,
    ast.Sub: operator.sub,
    ast.Mult: operator.mul,
    ast.Div: operator.truediv,
    ast.FloorDiv: operator.floordiv,
    ast.Mod: operator.mod,
    ast.Pow: operator.pow,
}

# Allowed unary operators
_UNARY_OPERATORS = {
    ast.UAdd: operator.pos,
    ast.USub: operator.neg,
}

# Safe function allowlist
_SAFE_FUNCTIONS = {
    "abs": abs,
    "round": round,
    "min": min,
    "max": max,
    "sum": sum,
    "len": len,
    "sqrt": math.sqrt,
    "ceil": math.ceil,
    "floor": math.floor,
    "log": math.log,
    "log10": math.log10,
    "pow": pow,
}


def _safe_eval(node: ast.AST) -> Any:
    """Recursively evaluate an AST node with strict allowlisting."""
    if isinstance(node, ast.Expression):
        return _safe_eval(node.body)

    if isinstance(node, ast.Constant):
        if isinstance(node.value, (int, float)):
            return node.value
        raise ValueError(f"Unsupported constant type: {type(node.value).__name__}")

    if isinstance(node, ast.BinOp):
        op_type = type(node.op)
        if op_type not in _OPERATORS:
            raise ValueError(f"Unsupported operator: {op_type.__name__}")
        left = _safe_eval(node.left)
        right = _safe_eval(node.right)
        return _OPERATORS[op_type](left, right)

    if isinstance(node, ast.UnaryOp):
        op_type = type(node.op)
        if op_type not in _UNARY_OPERATORS:
            raise ValueError(f"Unsupported unary operator: {op_type.__name__}")
        return _UNARY_OPERATORS[op_type](_safe_eval(node.operand))

    if isinstance(node, ast.Call):
        if not isinstance(node.func, ast.Name):
            raise ValueError("Only simple function calls allowed")
        func_name = node.func.id
        if func_name not in _SAFE_FUNCTIONS:
            raise ValueError(f"Function not allowed: {func_name}")
        args = [_safe_eval(arg) for arg in node.args]
        return _SAFE_FUNCTIONS[func_name](*args)

    if isinstance(node, ast.Tuple | ast.List):
        return [_safe_eval(elt) for elt in node.elts]

    raise ValueError(f"Unsupported expression type: {type(node).__name__}")


async def calculate(
    expression: str,
    precision: int = 4,
) -> dict:
    """Evaluate a mathematical expression and return the result.

    Use this tool whenever you need to compute percentages, rates,
    ratios, or any arithmetic on numbers from other tool responses.
    Do NOT do mental math — always use this tool for accuracy.

    Examples:
      calculate(expression="48 / (184 + 48) * 100")  →  20.6897 (error rate %)
      calculate(expression="1 - 0.85")                →  0.15
      calculate(expression="round(3.14159, 2)")        →  3.14
      calculate(expression="max(10, 20, 5)")           →  20

    Args:
        expression: A mathematical expression using numbers and operators (+, -, *, /, **, %). Supports functions: abs, round, min, max, sum, sqrt, ceil, floor, log, log10, pow
        precision: Number of decimal places to round to (default 4)
    """
    if not expression or not expression.strip():
        return {"status": "error", "message": "Expression cannot be empty"}

    try:
        tree = ast.parse(expression.strip(), mode="eval")
        result = _safe_eval(tree)
        # Round to requested precision
        if isinstance(result, float):
            result = round(result, precision)
        return {"status": "success", "result": result, "expression": expression.strip()}
    except ZeroDivisionError:
        return {"status": "error", "message": "Division by zero"}
    except (ValueError, TypeError, SyntaxError) as e:
        return {"status": "error", "message": f"Invalid expression: {e}"}
