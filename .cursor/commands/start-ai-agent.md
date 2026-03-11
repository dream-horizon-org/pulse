Start the Pulse AI agent for development.

1. Change to `pulse_ai/`
2. Check if `.env` exists, if not copy from `.env.example` and remind user to set `GOOGLE_API_KEY`
3. Run `./setup.sh` to build and start the Docker container (port 8000)
4. Verify health: `curl -f http://localhost:8000`
5. If already running, use `./setup.sh restart` to rebuild and restart
6. To view logs: `./setup.sh logs`
7. To stop: `./setup.sh stop`
