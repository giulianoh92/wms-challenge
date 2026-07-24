#!/bin/bash
# Levanta la aplicación. Uso: ./run.sh [PUERTO]   (por defecto 8080)
set -e

PORT="${1:-8080}"

echo "Levantando la app en el puerto $PORT..."
echo "API base:  http://localhost:$PORT/api/templates"
echo "Swagger UI: http://localhost:$PORT/api/templates/documentation"
echo ""

./gradlew bootRun --args="--server.port=$PORT --spring.profiles.active=local"
