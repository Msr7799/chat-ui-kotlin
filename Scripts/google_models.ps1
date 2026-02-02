$p   = "chat-ui-e1c11"
$loc = "us-central1"
$t   = (gcloud auth application-default print-access-token)

$h = @{
  Authorization       = "Bearer $t"
  "x-goog-user-project" = $p
}

$base = "https://$loc-aiplatform.googleapis.com/v1beta1/publishers/google/models?pageSize=200"
$r = Invoke-RestMethod -Headers $h -Uri $base

$r.publisherModels.name | Where-Object { $_ -match "/models/(gemini|veo-|imagen)" }
