from pydantic import BaseModel


class PlanOutput(BaseModel):
    """Structured output schema for the Planner agent."""

    intent_clear: bool
    query_understanding: str
    selected_personas: list[str]
    analysis_focus: str
    clarification_needed: str | None = None
