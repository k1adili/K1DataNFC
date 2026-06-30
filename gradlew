#!/bin/sh
APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`
APP_HOME=`pwd -P`
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
JAVA_OPTS=""
GRADLE_OPTS=""
DEFAULT_JVM_OPTS=""
set -- "$@"
die () { echo; echo "ERROR: $*"; echo; exit 1; } >&2
warn () { echo "$*"; } >&2
if [ ! -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
  if command -v curl > /dev/null; then
    curl -L "https://services.gradle.org/distributions/gradle-8.2-bin.zip" > /tmp/gradle.zip 2>/dev/null
  fi
fi
exec java $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
