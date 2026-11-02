@echo off
chcp 65001 >nul
rem Roda os testes automatizados (JUnit 5). Requer um banco de TESTES (o nome precisa conter "test").
rem   docker exec -it loja-db psql -U postgres -c "CREATE DATABASE loja_cupcakes_test"
rem Variaveis opcionais: TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD
set JUNIT=lib\junit-platform-console-standalone.jar
set PG=lib\postgresql-42.7.2.jar
if not exist out mkdir out
if not exist out-test mkdir out-test
if not exist relatorios mkdir relatorios
dir /s /b src\*.java > fontes.txt
javac -encoding UTF-8 -d out -cp %PG% @fontes.txt || goto :erro
dir /s /b test\*.java > fontes-test.txt
javac -encoding UTF-8 -d out-test -cp out;%PG%;%JUNIT% @fontes-test.txt || goto :erro
java -Dstdout.encoding=UTF-8 -jar %JUNIT% -cp "out;out-test;%PG%" --scan-classpath out-test --details=tree --disable-banner --reports-dir relatorios
goto :fim
:erro
echo Falha na compilacao.
:fim
del fontes.txt fontes-test.txt 2>nul
