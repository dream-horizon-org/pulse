from pydantic import BaseModel


class InteractionObservation(BaseModel):
    interaction_name: str
    hypothesis: str  # qualitative, 5–15 words, no numbers


class InteractionOverviewOutputV1(BaseModel):
    poor_interactions: list[InteractionObservation]
    fair_or_elevated_interactions: list[InteractionObservation]
    trend_note: str | None = None  # qualitative only, no numbers; None if nothing notable
    business_impact: str  # short qualitative phrase on user/business consequence, no numbers
    context: str  # machine-readable snapshot for next run (internal — never shown to user)
