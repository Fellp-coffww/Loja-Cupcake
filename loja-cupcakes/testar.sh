#!/bin/sh
# Roda os testes automatizados (JUnit 5). Requer um banco de TESTES (o nome precisa conter "test").
#   createdb -U postgres loja_cupcakes_test
# Variáveis opcionais: TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD
JUNIT=lib/junit-platform-console-standalone.jar
PG=lib/postgresql-42.7.2.jar
mkdir -p out out-test relatorios
javac -encoding UTF-8 -d out -cp $PG $(find src -name "*.java") || exit 1
javac -encoding UTF-8 -d out-test -cp out:$PG:$JUNIT $(find test -name "*.java") || exit 1
java -Dstdout.encoding=UTF-8 -jar $JUNIT -cp out:out-test:$PG --scan-classpath out-test --details=tree --disable-banner \
     --reports-dir relatorios
