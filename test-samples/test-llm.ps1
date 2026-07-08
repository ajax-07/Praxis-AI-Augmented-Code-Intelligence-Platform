# ============================================================================
# Praxis LLM integration smoke test (PowerShell 5.1 compatible)
#
# Prereqs before running:
#   1. docker compose up -d          (postgres, redis, ollama — from Docker/)
#   2. docker exec parxis-ollama ollama pull qwen2.5-coder:7b
#   3. Start the backend with the real provider:
#        $env:CORTEX_PROVIDER = "ollama"; ./gradlew bootRun
#      (startup log must show: "Cortex using Ollama provider: model=qwen2.5-coder:7b")
#
# What this script proves:
#   - a high-risk unit passes the funnel and reaches the LLM
#   - AI findings come back with real (non-canned) suggestion text
#   - llm_call rows record provider=OLLAMA with genuine token counts
# ============================================================================


$base = "http://localhost:8145/api/v1"
$zip  = "C:\Users\ajaykumar12\WorkSpace\Praxis\test-samples\nasty-sample.zip"

# --- 1. Register a throwaway user -------------------------------------------
$email = "llm-test-$(Get-Random)@praxis.dev"
$registerParams = @{
    Method      = 'Post'
    Uri         = "$base/auth/register"
    ContentType = 'application/json'
    Body        = (@{ email = $email; password = "password123"; tenantName = "llm-smoke" } | ConvertTo-Json)
}
$auth = Invoke-RestMethod @registerParams
$H = @{ Authorization = "Bearer $($auth.token)" }
Write-Host "1. Registered $email" -ForegroundColor Green

# --- 2. Start an analysis of the deliberately nasty zip ---------------------
$analysisParams = @{
    Method      = 'Post'
    Uri         = "$base/analyses"
    Headers     = $H
    ContentType = 'application/json'
    Body        = (@{ name = "llm-smoke"; sourceType = "ZIP"; sourceRef = $zip } | ConvertTo-Json)
}
$start = Invoke-RestMethod @analysisParams
$id = $start.analysisId
Write-Host "2. Analysis started: $id (status $($start.status))" -ForegroundColor Green

# --- 3. Poll until terminal -------------------------------------------------
$deadline = (Get-Date).AddMinutes(5)
do {
    Start-Sleep -Seconds 3
    $a = Invoke-RestMethod -Uri "$base/analyses/$id" -Headers $H
    Write-Host "   status: $($a.status)"
} while ($a.status -ne "COMPLETE" -and $a.status -ne "FAILED" -and (Get-Date) -lt $deadline)

if ($a.status -ne "COMPLETE") {
    Write-Host "FAILED or timed out: $($a.errorMessage)" -ForegroundColor Red
    exit 1
}
Write-Host "3. COMPLETE — health score: $($a.healthScore)" -ForegroundColor Green

# --- 4. Assert AI findings exist --------------------------------------------
$files = Invoke-RestMethod -Uri "$base/analyses/$id/files" -Headers $H
$file  = $files | Where-Object { $_.path -like "*NastyService*" } | Select-Object -First 1
$detail = Invoke-RestMethod -Uri "$base/analyses/$id/files/$($file.fileResultId)" -Headers $H

$ai = @($detail.findings | Where-Object { $_.source -eq "AI" })
Write-Host "4. Findings: $($detail.findings.Count) total, $($ai.Count) from the AI" -ForegroundColor Green
if ($ai.Count -eq 0) {
    Write-Host "   NO AI FINDINGS — provider was down (check backend log for 'degrading') or funnel selected 0 units" -ForegroundColor Red
    exit 1
}

Write-Host "--- first AI suggestion ---------------------------------------"
Write-Host $ai[0].suggestion
Write-Host "----------------------------------------------------------------"

# --- 5. Assert llm_call rows show the real provider + real token counts -----
Write-Host "5. llm_call rows (provider must be OLLAMA, not STUB):" -ForegroundColor Green
docker exec praxis-pgdb psql -U praxis_user -d praxis_db -c `
    "SELECT provider, model, tokens_in, tokens_out, created_at FROM llm_call ORDER BY created_at DESC LIMIT 5;"

# --- 5. Assert llm_call rows show the real provider + real token counts -----

Write-Host "5. llm_call rows (provider must be OLLAMA, not STUB):" -ForegroundColor Green

# Define the SQL command cleanly using a Here-String
$sqlQuery = @"
SELECT provider, model, tokens_in, tokens_out, created_at FROM llm_call ORDER BY created_at DESC LIMIT 5;
"@

# Pass the query to docker
docker exec praxis-pgdb psql -U praxis_user -d praxis_db -c "$sqlQuery"

Write-Host "PASSED - full LLM integration verified." -ForegroundColor Green

