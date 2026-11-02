@echo off
if not exist out mkdir out
dir /s /b src\*.java > fontes.txt
javac -encoding UTF-8 -d out -cp lib\postgresql-42.7.2.jar @fontes.txt && echo Compilado em .\out
del fontes.txt
