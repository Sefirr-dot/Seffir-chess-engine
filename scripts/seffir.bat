@echo off
rem Launches the Seffir UCI engine. Point your chess GUI (Arena, CuteChess, BanksiaGUI...) at this file.
java -jar "%~dp0..\engine-uci\target\seffir.jar" %*
