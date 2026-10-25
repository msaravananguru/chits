@echo off
rem ============================================================
rem  Sudar Chits - starts the backend and the frontend together.
rem  Double-click this file (or run run-all.bat in this folder).
rem  Close the two black windows it opens to stop everything.
rem ============================================================
setlocal
cd /d "%~dp0"

echo.
echo  Sudar Chits
echo  -----------

rem ---- checks -------------------------------------------------
where java >nul 2>nul
if errorlevel 1 (
  echo  [ERROR] Java was not found. Install JDK 21 and add it to PATH.
  pause
  exit /b 1
)
where npm >nul 2>nul
if errorlevel 1 (
  echo  [ERROR] Node.js / npm was not found. Install Node.js first.
  pause
  exit /b 1
)

rem ---- port 8080 must be free (stop the Eclipse run first) -----
netstat -ano | findstr /r /c:":8080 .*LISTENING" >nul
if not errorlevel 1 (
  echo  [ERROR] Port 8080 is already in use.
  echo          Stop the app running in Eclipse ^(red Stop button^) and run this again.
  pause
  exit /b 1
)

rem ---- first time only: install the frontend packages ----------
if not exist "frontend\node_modules" (
  echo  Installing frontend packages ^(first time only^)...
  pushd frontend
  call npm install
  popd
)

rem ---- start the backend in its own window ----------------------
echo  Starting backend  ^(http://localhost:8080^) ...
start "Chits - BACKEND (close to stop)" cmd /k "cd /d "%~dp0" && mvnw.cmd spring-boot:run"

rem ---- start the frontend in its own window ---------------------
echo  Starting frontend ^(http://localhost:5173^) ...
start "Chits - FRONTEND (close to stop)" cmd /k "cd /d "%~dp0frontend" && npm run dev -- --host"

rem ---- show the phone address -----------------------------------
set "IP="
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /c:"IPv4"') do if not defined IP set "IP=%%a"
set "IP=%IP: =%"

echo.
echo  PC     : http://localhost:5173
if defined IP echo  Phone  : http://%IP%:5173   ^(same Wi-Fi^)
echo.
echo  The backend takes about 20-40 seconds to start. Opening the browser...

timeout /t 25 /nobreak >nul
start "" http://localhost:5173

echo.
echo  Done. You can close this window; the two other windows keep running.
timeout /t 5 >nul
endlocal
