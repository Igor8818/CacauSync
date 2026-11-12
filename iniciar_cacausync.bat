@echo off
title CacauSync API - Servidor Local
echo ========================================================
echo       🌱 CacauSync - Servidor CEPEC / CEPLAC
echo ========================================================
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "PATH=%JAVA_HOME%\bin;C:\Users\Estagio\Downloads\apache-maven-3.9.9\bin;%PATH%"
cd /d "C:\Users\Estagio\Downloads\CacauSync"

echo.
echo [INFO] IP Local deste computador: 192.168.2.13
echo [INFO] Porta: 8080
echo [INFO] Painel Web: http://localhost:8080 ou http://192.168.2.13:8080
echo.
echo Iniciando servidor Spring Boot em modo local...
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
pause
