Build the Pulse AI agent Docker image.

1. Change to `pulse_ai/`
2. Check if `.env` exists, if not copy from `.env.example` and remind user to set `GOOGLE_API_KEY`
3. Run `./setup.sh` to build and start the Docker container
4. Verify health: `curl -f http://localhost:8000`
5. If the build fails, check logs with `./setup.sh logs` and suggest fixes
6. If it passes, report success
