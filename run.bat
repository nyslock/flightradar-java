@echo off
REM Launch the FlightRadar JavaFX app via the Maven javafx plugin.
REM Run by double-clicking this file or executing it from a terminal.
cd /d "%~dp0"
call mvn javafx:run
pause
