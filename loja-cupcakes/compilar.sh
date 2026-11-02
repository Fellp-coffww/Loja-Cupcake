#!/bin/sh
# Compila o projeto (requer JDK 17+)
mkdir -p out
javac -encoding UTF-8 -d out -cp lib/postgresql-42.7.2.jar $(find src -name "*.java") && echo "Compilado em ./out"
