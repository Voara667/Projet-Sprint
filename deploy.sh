#!/usr/bin/env bash

# Deploy script for the framework
TEST_APP_DIR="../test-app"
BUILD_DIR="build"
JAR_NAME="framework.jar"

SPRING_VERSION="6.2.18"
SPRING_JARS=(
  "spring-core-${SPRING_VERSION}.jar"
  "spring-context-${SPRING_VERSION}.jar"
  "spring-beans-${SPRING_VERSION}.jar"
  "spring-web-${SPRING_VERSION}.jar"
  "spring-expression-${SPRING_VERSION}.jar"
  "spring-aop-${SPRING_VERSION}.jar"
  "spring-jcl-${SPRING_VERSION}.jar"
)

echo "Starting deploy.sh"

# Auto-detect CATALINA_HOME if not set
if [ -z "$CATALINA_HOME" ]; then
  if [ -d "/home/voara/Documents/tomcat/tomcat" ]; then
    export CATALINA_HOME="/home/voara/Documents/tomcat/tomcat"
    echo "CATALINA_HOME auto-detected: $CATALINA_HOME"
  else
    echo "Warning: CATALINA_HOME is not set and could not be auto-detected. Attempting to continue, but compilation may fail." >&2
  fi
fi

# Locate servlet API jar
SERVLET_JAR=""
if [ -n "$CATALINA_HOME" ] && [ -d "$CATALINA_HOME/lib" ]; then
  SERVLET_JAR=$(ls "$CATALINA_HOME"/lib/*servlet* 2>/dev/null | head -n 1 || true)
fi

# Locate Spring jars from local Maven repository
SPRING_CP=""
for jar in "${SPRING_JARS[@]}"; do
  found=$(find "$HOME/.m2/repository/org/springframework" -name "$jar" 2>/dev/null | head -n 1 || true)
  if [ -z "$found" ]; then
    echo "Erreur: jar Spring manquant dans le repository Maven local: $jar" >&2
  else
    SPRING_CP="$SPRING_CP:$found"
  fi
done

if [ -z "$SERVLET_JAR" ]; then
  echo "Erreur: impossible de localiser le jar de l'API Servlet (cherchez dans CATALINA_HOME/lib)." >&2
  echo "Compilation du framework : ECHOUÉ" >&2
elif [ -z "$SPRING_CP" ]; then
  echo "Erreur: impossible de localiser les jars Spring requis dans ~/.m2/repository." >&2
  echo "Compilation du framework : ECHOUÉ" >&2
else
  echo "Utilisation du servlet jar : $SERVLET_JAR"
  echo "Utilisation des jars Spring : $SPRING_CP"

  # Compile framework sources
  mkdir -p "$BUILD_DIR"
  SRC_FILES=$(find src -name "*.java")
  if [ -z "$SRC_FILES" ]; then
    echo "Aucun fichier source trouvé dans src/." >&2
    echo "Compilation du framework : ECHOUÉ" >&2
  else
    javac -cp "$SERVLET_JAR$SPRING_CP" -d "$BUILD_DIR" $SRC_FILES
    if [ $? -ne 0 ]; then
      echo "Erreur lors de la compilation des sources du framework." >&2
      echo "Compilation du framework : ECHOUÉ" >&2
    else
      echo "Compilation du framework : OK"

      # Package into jar
      jar cf "$JAR_NAME" -C "$BUILD_DIR" .
      if [ $? -ne 0 ]; then
        echo "Erreur lors de la creation de $JAR_NAME" >&2
      else
        echo "Archive $JAR_NAME cree : OK"

        # Copy to test app lib
        if [ -d "$TEST_APP_DIR/WEB-INF/lib" ]; then
          cp -f "$JAR_NAME" "$TEST_APP_DIR/WEB-INF/lib/"
          if [ $? -ne 0 ]; then
            echo "Erreur lors de la copie de $JAR_NAME vers $TEST_APP_DIR/WEB-INF/lib/" >&2
          else
            echo "Copie de $JAR_NAME vers $TEST_APP_DIR/WEB-INF/lib/ : OK"
          fi
        else
          echo "Dossier $TEST_APP_DIR/WEB-INF/lib/ introuvable. Creation du dossier." 
          mkdir -p "$TEST_APP_DIR/WEB-INF/lib"
          cp -f "$JAR_NAME" "$TEST_APP_DIR/WEB-INF/lib/" && echo "Copie de $JAR_NAME vers $TEST_APP_DIR/WEB-INF/lib/ : OK"
        fi

        # Copy Spring jars to test-app lib if needed
        mkdir -p "$TEST_APP_DIR/WEB-INF/lib"
        for jar in "${SPRING_JARS[@]}"; do
          found=$(find "$HOME/.m2/repository/org/springframework" -name "$jar" 2>/dev/null | head -n 1 || true)
          if [ -n "$found" ]; then
            cp -f "$found" "$TEST_APP_DIR/WEB-INF/lib/"
          fi
        done
        echo "Spring jars copies dans $TEST_APP_DIR/WEB-INF/lib/ : OK"

        # Compile all test-app controllers
        CONTROLLER_SOURCES=$(find "$TEST_APP_DIR/WEB-INF/classes" -name "*.java")
        if [ -n "$CONTROLLER_SOURCES" ]; then
          javac -cp "$SERVLET_JAR:$TEST_APP_DIR/WEB-INF/lib/$JAR_NAME:$SPRING_CP" -d "$TEST_APP_DIR/WEB-INF/classes" $CONTROLLER_SOURCES
          if [ $? -ne 0 ]; then
            echo "Erreur lors de la compilation des controllers de test-app." >&2
          else
            echo "Compilation des controllers de test-app : OK"
          fi
        else
          echo "Aucun controller test-app trouve, compilation des controllers ignoree." >&2
        fi
      fi
    fi
  fi
fi

echo "deploy.sh terminé"

# Auto-deploy to Tomcat
if [ -n "$CATALINA_HOME" ] && [ -d "$CATALINA_HOME/webapps" ]; then
  echo ""
  echo "Deploiement automatique sur Tomcat..."
  
  # Remove existing test-app if present
  if [ -d "$CATALINA_HOME/webapps/test-app" ]; then
    rm -rf "$CATALINA_HOME/webapps/test-app"
    echo "Ancien deploiement supprime."
  fi
  
  # Copy test-app to Tomcat
  cp -r "$TEST_APP_DIR" "$CATALINA_HOME/webapps/" 2>/dev/null
  if [ $? -ne 0 ]; then
    echo "Erreur lors de la copie de test-app vers $CATALINA_HOME/webapps/" >&2
  else
    echo "Copie de test-app vers Tomcat : OK"
    
    # Restart Tomcat
    echo "Redemarrage de Tomcat..."
    "$CATALINA_HOME/bin/shutdown.sh" >/dev/null 2>&1 || true
    sleep 2
    "$CATALINA_HOME/bin/startup.sh" >/dev/null 2>&1
    if [ $? -eq 0 ]; then
      echo "Tomcat redemarre : OK"
      echo ""
      echo "Application disponible sur http://localhost:8080/test-app/aaa"
      sleep 3
    else
      echo "Redemarrage de Tomcat : tentative faite (voir logs Tomcat pour details)."
    fi
  fi
else
  echo "CATALINA_HOME non defini ou webapps inaccessible. Deploiement manuel requis." >&2
fi
