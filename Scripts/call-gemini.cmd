@echo off
setlocal enabledelayedexpansion

set "PROJECT_ID=chat-ui-e1c11"
set "LOCATION=us-central1"
set "MODEL=gemini-2.0-flash-001"

REM Get access token with error handling
for /f "delims=" %%i in ('gcloud auth application-default print-access-token 2^>nul') do set "TOKEN=%%i"

if not defined TOKEN (
  echo Error: Failed to get access token. Please run: gcloud auth application-default login
  exit /b 1
)

set "REQ=%TEMP%\vertex_gemini_req.json"
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

curl -sS --fail-with-body -X POST ^
  -H "Authorization: Bearer !TOKEN!" ^
  -H "Content-Type: application/json" ^
  -H "x-goog-user-project: %PROJECT_ID%" ^
  "https://%LOCATION%-aiplatform.googleapis.com/v1/projects/%PROJECT_ID%/locations/%LOCATION%/publishers/google/models/%MODEL%:generateContent" ^
  --data-binary "@%REQ%"

if %errorlevel% neq 0 (
  echo.
  echo Error: Failed to connect to Vertex AI API
)

endlocal