"""Tests for the calculate utility tool.

TDD RED: Tests written before pulse_ai/tools/utils/calculate.py exists.
"""

import pytest


class TestCalculate:
    """Tests for the calculate tool."""

    @pytest.mark.asyncio
    async def test_basic_addition(self):
        from pulse_ai.tools.utils.calculate import calculate

        result = await calculate(expression="2 + 3")
        assert result["status"] == "success"
        assert result["result"] == 5

    @pytest.mark.asyncio
    async def test_basic_division(self):
        from pulse_ai.tools.utils.calculate import calculate

        result = await calculate(expression="48 / (184 + 48) * 100")
        assert result["status"] == "success"
        assert abs(result["result"] - 20.69) < 0.01

    @pytest.mark.asyncio
    async def test_percentage_calculation(self):
        from pulse_ai.tools.utils.calculate import calculate

        result = await calculate(expression="(30 / 120) * 100")
        assert result["status"] == "success"
        assert result["result"] == 25.0

    @pytest.mark.asyncio
    async def test_complex_expression(self):
        from pulse_ai.tools.utils.calculate import calculate

        result = await calculate(expression="(0.85 * 100 + 0.15 * 50) / 100")
        assert result["status"] == "success"
        assert abs(result["result"] - 0.925) < 0.001

    @pytest.mark.asyncio
    async def test_rounding(self):
        from pulse_ai.tools.utils.calculate import calculate

        result = await calculate(expression="1 / 3 * 100", precision=2)
        assert result["status"] == "success"
        assert result["result"] == 33.33

    @pytest.mark.asyncio
    async def test_default_precision(self):
        from pulse_ai.tools.utils.calculate import calculate

        result = await calculate(expression="1 / 3 * 100")
        assert result["status"] == "success"
        # Default precision is 4
        assert result["result"] == 33.3333

    @pytest.mark.asyncio
    async def test_division_by_zero(self):
        from pulse_ai.tools.utils.calculate import calculate

        result = await calculate(expression="10 / 0")
        assert result["status"] == "error"
        assert "division" in result["message"].lower()

    @pytest.mark.asyncio
    async def test_invalid_expression(self):
        from pulse_ai.tools.utils.calculate import calculate

        result = await calculate(expression="hello + world")
        assert result["status"] == "error"

    @pytest.mark.asyncio
    async def test_unsafe_expression_import(self):
        """Ensure import statements are blocked."""
        from pulse_ai.tools.utils.calculate import calculate

        result = await calculate(expression="__import__('os').system('ls')")
        assert result["status"] == "error"

    @pytest.mark.asyncio
    async def test_unsafe_expression_builtin(self):
        """Ensure builtins like open() are blocked."""
        from pulse_ai.tools.utils.calculate import calculate

        result = await calculate(expression="open('/etc/passwd')")
        assert result["status"] == "error"

    @pytest.mark.asyncio
    async def test_math_functions_allowed(self):
        """sqrt, abs, min, max, round should work."""
        from pulse_ai.tools.utils.calculate import calculate

        result = await calculate(expression="round(3.14159, 2)")
        assert result["status"] == "success"
        assert result["result"] == 3.14

    @pytest.mark.asyncio
    async def test_min_max(self):
        from pulse_ai.tools.utils.calculate import calculate

        result = await calculate(expression="max(10, 20, 5)")
        assert result["status"] == "success"
        assert result["result"] == 20

    @pytest.mark.asyncio
    async def test_empty_expression(self):
        from pulse_ai.tools.utils.calculate import calculate

        result = await calculate(expression="")
        assert result["status"] == "error"
