@echo off
rem Starts the Seffir desktop app. Build it first with: mvnw package
start "" javaw -jar "%~dp0..\gui\target\seffir-gui.jar" %*
