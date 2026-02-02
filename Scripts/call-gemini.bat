@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "PROJECT_ID=chat-ui-e1c11"
set "LOCATION=us-central1"
set "MODEL=gemini-2.0-flash-001"

set "API_KEY=AIzaSyDu1AfHVVHPyDpeuOnvgomTJ4a4JBjxxT0" REM  Replace with your actual API Key

set "REQ=%TEMP%\vertex_gemini_req.json"
set "OUT=%TEMP%\vertex_gemini_out.json"

REM Create JSON request file
(
  echo {
  echo   "contents": [
  echo     {
  echo       "role": "user",
  echo       "parts": [
  echo         {
  echo           "text": "Reply with exactly: OK - Vertex AI is responding."
  echo         }
  echo       ]
  echo     }
  echo   ],
  echo   "generationConfig": {
  echo     "temperature": 0,
  echo     "maxOutputTokens": 32
  echo   }
  echo }
) > "%REQ%"

REM Send request to Vertex AI
curl -sS --fail-with-body -X POST ^
  -H "Content-Type: application/json" ^
  -H "x-goog-user-project: %PROJECT_ID%" ^
  "https://%LOCATION%-aiplatform.googleapis.com/v1/projects/%PROJECT_ID%/locations/%LOCATION%/publishers/google/models/%MODEL%:generateContent?key=%API_KEY%" ^
  --data-binary "@%REQ%"

if errorlevel 1 (
  echo ERROR: Vertex AI request failed.
  type "%OUT%"
  exit /b 1
)

REM Extract response text using PowerShell
for /f "usebackq delims=" %%t in (`powershell -NoProfile -Command "(Get-Content -Raw '%OUT%' | ConvertFrom-Json).candidates[0].content.parts[0].text"`) do set "TEXT=%%t"

echo %TEXT%
endlocal
exit /b 0
