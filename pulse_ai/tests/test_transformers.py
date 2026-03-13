"""Tests for transformers — columnar → dict, error parsing, value parsing.

TDD RED: All tests written before pulse_ai/transformers/response_transformer.py exists.
"""

import httpx
import pytest


# ===================================================================
# _parse_value() tests
# ===================================================================


def test_parse_value_integer():
    """Integer string returns int."""
    from pulse_ai.transformers.response_transformer import _parse_value
    assert _parse_value("42") == 42
    assert isinstance(_parse_value("42"), int)


def test_parse_value_float():
    """Float string returns rounded float (4 decimal places)."""
    from pulse_ai.transformers.response_transformer import _parse_value
    assert _parse_value("0.523456789") == 0.5235
    assert isinstance(_parse_value("0.523456789"), float)


def test_parse_value_plain_string():
    """Non-numeric string is returned as-is."""
    from pulse_ai.transformers.response_transformer import _parse_value
    assert _parse_value("ContestJoin") == "ContestJoin"


def test_parse_value_none():
    """None returns None."""
    from pulse_ai.transformers.response_transformer import _parse_value
    assert _parse_value(None) is None


def test_parse_value_empty_string():
    """Empty string returns empty string."""
    from pulse_ai.transformers.response_transformer import _parse_value
    assert _parse_value("") == ""


def test_parse_value_negative():
    """Negative number string parses correctly."""
    from pulse_ai.transformers.response_transformer import _parse_value
    assert _parse_value("-5") == -5
    assert _parse_value("-3.14") == -3.14


# ===================================================================
# transform_columnar() tests
# ===================================================================


def test_transform_columnar_basic():
    """Standard fields+rows converts to list of dicts."""
    from pulse_ai.transformers.response_transformer import transform_columnar

    data = {
        "fields": ["name", "apdex", "p50"],
        "rows": [
            ["ContestJoin", "0.52", "890"],
            ["MatchEntry", "0.85", "320"],
        ],
    }

    result = transform_columnar(data)

    assert len(result) == 2
    assert result[0] == {"name": "ContestJoin", "apdex": 0.52, "p50": 890}
    assert result[1] == {"name": "MatchEntry", "apdex": 0.85, "p50": 320}


def test_transform_columnar_empty_rows():
    """Empty rows returns empty list."""
    from pulse_ai.transformers.response_transformer import transform_columnar

    result = transform_columnar({"fields": ["name", "apdex"], "rows": []})

    assert result == []


def test_transform_columnar_empty_data():
    """Missing or empty data dict returns empty list."""
    from pulse_ai.transformers.response_transformer import transform_columnar

    assert transform_columnar({}) == []
    assert transform_columnar(None) == []


def test_transform_columnar_single_row():
    """Single row works correctly."""
    from pulse_ai.transformers.response_transformer import transform_columnar

    data = {
        "fields": ["interaction_name", "error_count"],
        "rows": [["PaymentFlow", "15"]],
    }

    result = transform_columnar(data)

    assert result == [{"interaction_name": "PaymentFlow", "error_count": 15}]


# ===================================================================
# parse_error_response() tests
# ===================================================================


def test_parse_error_standard_format():
    """Standard error format: {data: null, error: {code, message}}."""
    from pulse_ai.transformers.response_transformer import parse_error_response

    response = httpx.Response(
        400,
        json={"data": None, "error": {"code": "BAD_REQUEST", "message": "Invalid query"}},
        request=httpx.Request("POST", "http://test"),
    )

    result = parse_error_response(response)

    assert result["status"] == "error"
    assert result["message"] == "Invalid query"


def test_parse_error_validation_format():
    """Validation error format: {errors: ["msg1", "msg2"]}."""
    from pulse_ai.transformers.response_transformer import parse_error_response

    response = httpx.Response(
        400,
        json={"errors": [
            "name Interaction name cannot be blank",
            "events must have at least two element",
        ]},
        request=httpx.Request("POST", "http://test"),
    )

    result = parse_error_response(response)

    assert result["status"] == "error"
    assert "name Interaction name cannot be blank" in result["message"]
    assert "events must have at least two element" in result["message"]


def test_parse_error_unparseable():
    """Non-JSON response still returns structured error."""
    from pulse_ai.transformers.response_transformer import parse_error_response

    response = httpx.Response(
        500,
        text="Internal Server Error",
        request=httpx.Request("GET", "http://test"),
    )

    result = parse_error_response(response)

    assert result["status"] == "error"
    assert "500" in result["message"]


def test_parse_error_401_unauthorized():
    """401 returns structured error with UNAUTHORIZED context."""
    from pulse_ai.transformers.response_transformer import parse_error_response

    response = httpx.Response(
        401,
        json={"error": {"code": "UNAUTHORIZED", "message": "Token expired"}},
        request=httpx.Request("GET", "http://test"),
    )

    result = parse_error_response(response)

    assert result["status"] == "error"
    assert "Token expired" in result["message"]
