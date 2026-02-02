#Requires -Version 5.0

param(
    [string]$ProjectId = "chat-ui-e1c11",
    [string]$Location = "us-central1",
    [string]$Model = "gemini-2.0-flash-001",
    [string]$Prompt = "Reply with exactly: OK - Vertex AI is responding."
)

# Get access token
try {
    Write-Host "Getting access token..." -ForegroundColor Cyan
    $token = & gcloud auth application-default print-access-token 2>$null
    if (!$token) {
        throw "Failed to get access token"
    }
} catch {
    Write-Host "ERROR: $_" -ForegroundColor Red
    Write-Host "Please run: gcloud auth application-default login" -ForegroundColor Yellow
    exit 1
}

# Create request JSON
$requestBody = @{
    contents = @(
        @{
            role = "user"
            parts = @(
                @{
                    text = $Prompt
                }
            )
        }
    )
    generationConfig = @{
        temperature = 0
        maxOutputTokens = 32
    }
} | ConvertTo-Json -Depth 10

# Send request to Vertex AI API
$uri = "https://$Location-aiplatform.googleapis.com/v1/projects/$ProjectId/locations/$Location/publishers/google/models/$Model`:generateContent"

try {
    Write-Host "Sending request to Vertex AI..." -ForegroundColor Cyan
    $response = Invoke-WebRequest -Uri $uri `
        -Method POST `
        -Headers @{
            "Authorization" = "Bearer $token"
            "Content-Type" = "application/json"
            "x-goog-user-project" = $ProjectId
        } `
        -Body $requestBody `
        -ErrorAction Stop

    # Parse and extract response
    $responseJson = $response.Content | ConvertFrom-Json
    $responseText = $responseJson.candidates[0].content.parts[0].text

    Write-Host $responseText -ForegroundColor Green
    exit 0
} catch {
    Write-Host "ERROR: Failed to connect to Vertex AI API" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Yellow
    exit 1
}
