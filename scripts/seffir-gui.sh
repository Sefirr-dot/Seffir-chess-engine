#!/bin/sh
# Starts the Seffir desktop app. Build it first with: ./mvnw package
exec java -jar "$(dirname "$0")/../gui/target/seffir-gui.jar" "$@"
