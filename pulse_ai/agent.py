import os

from dotenv import load_dotenv
from google.adk.agents.llm_agent import Agent

from .constants import AGENT_MODEL_ENV_KEY, DEFAULT_MODEL

load_dotenv()

agent_model = os.getenv(AGENT_MODEL_ENV_KEY, DEFAULT_MODEL)

root_agent = Agent(
    model=agent_model,
    name='root_agent',
    description='A helpful assistant for user questions.',
    instruction='Answer user questions to the best of your knowledge',
)
