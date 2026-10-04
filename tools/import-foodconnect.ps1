param([string]$ApiUrl = 'http://localhost:8000')
$ErrorActionPreference = 'Stop'
$seedPath = Join-Path $PSScriptRoot '../backend/seeds/foodconnect-providers.json'
$passwordPath = Join-Path $PSScriptRoot '../backend/data/local-staff-password.txt'
if (!(Test-Path -LiteralPath $seedPath)) { throw 'FoodConnect reviewed provider seed is not prepared yet.' }
if (!(Test-Path -LiteralPath $passwordPath)) { throw 'Start the local stack first.' }
$seed = Get-Content -LiteralPath $seedPath -Raw -Encoding UTF8 | ConvertFrom-Json
$staffId = (Get-Content -LiteralPath (Join-Path $PSScriptRoot '../backend/data/local-staff-user-id.txt') -Raw).Trim()
$loginBody = @{username=$staffId; password=(Get-Content -LiteralPath $passwordPath -Raw).Trim()} | ConvertTo-Json
$login = Invoke-RestMethod "$ApiUrl/api/admin/login" -Method Post -ContentType 'application/json' -Body $loginBody
$headers = @{Authorization="Bearer $($login.token)"}
try {
    $existing = Invoke-RestMethod "$ApiUrl/api/admin/providers" -Headers $headers
    $existingIds = @($existing | ForEach-Object { $_.id })
    $new = @($seed.records | Where-Object { $_.id -notin $existingIds })
    if ($new.Count -gt 0) {
        $body = @{source=$seed.source; permission=$seed.permission; records=$new} | ConvertTo-Json -Depth 30
        $import = Invoke-RestMethod "$ApiUrl/api/admin/import" -Method Post -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
        if ($import.accepted_pending -ne $new.Count -or $import.quarantined -ne 0 -or $import.suspected_duplicates -ne 0) {
            throw 'FoodConnect import requires staff review. Check provider records, quarantine and duplicates. No records published by this run.'
        }
    }
    $providers = Invoke-RestMethod "$ApiUrl/api/admin/providers" -Headers $headers
    $published = 0
    foreach ($record in $seed.records) {
        $stored = $providers | Where-Object { $_.id -eq $record.id } | Select-Object -First 1
        if (!$stored) { throw "Missing imported provider: $($record.id)" }
        if ($stored.state -eq 'PENDING') {
            $null = Invoke-RestMethod "$ApiUrl/api/admin/providers/$($record.id)/publish" -Method Post -Headers $headers
            $published++
        } elseif ($stored.state -ne 'APPROVED') { throw "Provider requires review: $($record.id) ($($stored.state))" }
    }
    $public = Invoke-RestMethod "$ApiUrl/api/providers"
    $ids = @($public.providers | ForEach-Object { $_.id })
    $missing = @($seed.records | Where-Object { $_.id -notin $ids })
    if ($missing.Count -gt 0) { throw 'Not all imported providers are publicly visible.' }
    Write-Output "FoodConnect: $($seed.records.Count) provider records visible; $published published in this run. Each publication has an audit trail."
} finally {
    $null = Invoke-RestMethod "$ApiUrl/api/admin/logout" -Method Post -Headers $headers
    $headers = $null; $login = $null; $loginBody = $null
}
