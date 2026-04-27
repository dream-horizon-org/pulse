"""Canonical error-attribution drill signals for RCA (pulse-server + pulse_ai).

Must stay aligned with
``org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionRcaDrillSignals#CANONICAL_FOR_RCA``.
Crash is intentionally excluded from RCA drill/NLP.
"""

from __future__ import annotations

from typing import Literal

# Order is part of the model contract (NLP output must match this order).
CANONICAL_FOR_RCA_SIGNALS: tuple[Literal["anr", "non_fatal", "api"], ...] = (
    "anr",
    "non_fatal",
    "api",
)
