#!/bin/sh
#
# Gradle wrapper script for Unix
#
DIRNAME="$(cd "$(dirname "$0")" && pwd)"
exec java $JAVA_OPTS -jar "$DIRNAME/gradle/wrapper/gradle-wrapper.jar" "$@"
