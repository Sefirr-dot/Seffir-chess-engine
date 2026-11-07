#!/bin/sh
# Launches the Seffir UCI engine. Point your chess GUI at this file.
exec java -jar "$(dirname "$0")/../engine-uci/target/seffir.jar" "$@"
