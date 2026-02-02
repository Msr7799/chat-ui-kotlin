@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "PROJECT_ID=chat-ui-e1c11"
set "LOCATION=us-central1"
set "MODEL=gemini-2.0-flash-001"

for /f "delims=" %%i in ('gcloud auth application-default print-access-token 2^>nul') do set "TOKEN=%%i"
if not defined TOKEN (
  echo ERROR: Failed to get access token. Run: gcloud auth application-default login
  exit /b 1
)

set "REQ=%TEMP%\vertex_gemini_req.json"
set "OUT=%TEMP%\vertex_gemini_out.json"

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
  --data-binary "@%REQ%" > "%OUT%"

if errorlevel 1 (
  echo ERROR: Vertex AI request failed.
  type "%OUT%"
  exit /b 1
)

for /f "usebackq delims=" %%t in (`powershell -NoProfile -Command "(Get-Content -Raw '%OUT%' | ConvertFrom-Json).candidates[0].content.parts[0].text"`) do set "TEXT=%%t"

echo %TEXT%
exit /b 0