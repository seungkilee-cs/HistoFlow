#!/bin/bash
docker compose -f docker/docker-compose.base.yml -f docker/docker-compose.dev.yml --profile dev up --build &
cd frontend && npm start
