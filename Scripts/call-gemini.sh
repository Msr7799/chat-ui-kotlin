#!/bin/bash

PROJECT_ID="chat-ui-e1c11"
LOCATION="us-central1"
MODEL="gemini-2.0-flash-001"

# Get access token with error handling
echo "Getting access token..." >&2
TOKEN=$(gcloud auth application-default print-access-token 2>/dev/null)
if [ -z "$TOKEN" ]; then
  echo "ERROR: Failed to get access token. Run: gcloud auth application-default login" >&2
  exit 1
fi

REQ_FILE="/tmp/vertex_gemini_req.json"
OUT_FILE="/tmp/vertex_gemini_out.json"

# Create JSON request file
cat > "$REQ_FILE" << 'EOF'
{
  "contents": [
    {
      "role": "user",
      "parts": [
        {
          "text": "Reply with exactly: OK - Vertex AI is responding."
        }
      ]
    }
  ],
  "generationConfig": {
    "temperature": 0,
    "maxOutputTokens": 32
  }
}
EOF

# Send request to Vertex AI
echo "Sending request to Vertex AI..." >&2
curl -sS --fail-with-body -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "x-goog-user-project: $PROJECT_ID" \
  "https://$LOCATION-aiplatform.googleapis.com/v1/projects/$PROJECT_ID/locations/$LOCATION/publishers/google/models/$MODEL:generateContent" \
  --data-binary "@$REQ_FILE" > "$OUT_FILE"

if [ $? -ne 0 ]; then
  echo "ERROR: Vertex AI request failed." >&2
  cat "$OUT_FILE" >&2
  exit 1
fi

# Extract response text using jq
TEXT=$(jq -r '.candidates[0].content.parts[0].text' "$OUT_FILE" 2>/dev/null)

if [ $? -ne 0 ]; then
  echo "ERROR: Failed to parse response." >&2
  cat "$OUT_FILE" >&2
  exit 1
fi

echo "$TEXT"
exit 0
