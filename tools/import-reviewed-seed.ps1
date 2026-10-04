param([string]$ApiUrl = 'http://localhost:8000', [string]$SeedPath = '')
$ErrorActionPreference = 'Stop'
$seedPath = if ($SeedPath) { $SeedPath } else { Join-Path $PSScriptRoot '../backend/seeds/verified-services.json' }
$seed = Get-Content -LiteralPath $seedPath -Raw -Encoding UTF8 | ConvertFrom-Json
$serviceIds = @($seed.records | Where-Object { $_.kind -ne 'organisation' } | ForEach-Object { $_.id })
$passwordPath = Join-Path $PSScriptRoot '../backend/data/local-admin-password.txt'
if (!(Test-Path -LiteralPath $passwordPath)) { throw 'Start the local stack first.' }
$public = Invoke-RestMethod "$ApiUrl/api/services"
if (@($serviceIds | Where-Object { $_ -notin $public.services.id }).Count -eq 0) {
    Write-Output 'Reviewed seed already published.'
    exit 0
}
$loginBody = @{email='admin@foodbridge.local'; password=(Get-Content -LiteralPath $passwordPath -Raw).Trim()} | ConvertTo-Json
$login = Invoke-RestMethod "$ApiUrl/api/admin/login" -Method Post -ContentType 'application/json' -Body $loginBody
$headers = @{Authorization="Bearer $($login.token)"}
try {
    $existingOrgs = Invoke-RestMethod "$ApiUrl/api/admin/organisations" -Headers $headers
    $existingServices = Invoke-RestMethod "$ApiUrl/api/admin/services" -Headers $headers
    $existingIds = @($existingOrgs.id) + @($existingServices.id)
    $new = @($seed.records | Where-Object { $_.id -notin $existingIds })
    if ($new.Count -gt 0) {
    $body = @{source=$seed.source; permission=$seed.permission; records=$new} | ConvertTo-Json -Depth 30
    $import = Invoke-RestMethod "$ApiUrl/api/admin/import" -Method Post -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
    if ($import.accepted_pending -ne $new.Count -or $import.quarantined -ne 0 -or $import.suspected_duplicates -ne 0) {
        throw 'Seed requires staff review: check quarantine, duplicates and pending records. No records published.'
    }
    }
    $orgs = Invoke-RestMethod "$ApiUrl/api/admin/organisations" -Headers $headers
    $services = Invoke-RestMethod "$ApiUrl/api/admin/services" -Headers $headers
    foreach ($record in ($seed.records | Sort-Object { $_.kind -ne 'organisation' })) {
        $collection = if ($record.kind -eq 'organisation') { 'organisations' } else { 'services' }
        $stored = @($orgs) + @($services) | Where-Object { $_.id -eq $record.id } | Select-Object -First 1
        if (!$stored) { throw "Missing seed record $($record.id)" }
        if ($stored.state -eq 'PENDING') { $null = Invoke-RestMethod "$ApiUrl/api/admin/$collection/$($record.id)/publish" -Method Post -Headers $headers }
        elseif ($stored.state -ne 'APPROVED') { throw "Seed record requires review: $($record.id)" }
    }
    Write-Output "Reviewed seed published with an audit trail ($($serviceIds.Count) services)."
} finally {
    $null = Invoke-RestMethod "$ApiUrl/api/admin/logout" -Method Post -Headers $headers
    $headers = $null
    $login = $null
    $loginBody = $null
}
