@echo off
rem Variaveis opcionais: DB_URL, DB_USER, DB_PASSWORD, PORT
java -cp "out;lib\postgresql-42.7.2.jar" loja.Main
