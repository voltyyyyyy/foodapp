$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$localData = Join-Path $repoRoot 'backend/data'
New-Item -ItemType Directory -Force $localData | Out-Null
function Get-LocalSecret([string]$Name) {
    $secretPath = Join-Path $localData $Name
    if (!(Test-Path $secretPath)) { [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N') | Set-Content $secretPath }
    return (Get-Content $secretPath -Raw).Trim()
}
# Optional routing key stays in ignored local data; never include it in console output.
$routesKeyPath = Join-Path $localData 'google-routes-api-key.txt'
if (Test-Path $routesKeyPath) { $env:GOOGLE_ROUTES_API_KEY = (Get-Content -LiteralPath $routesKeyPath -Raw).Trim() }
$dbPassword = Get-LocalSecret 'local-postgres-password.txt'
$staffPassword = Get-LocalSecret 'local-staff-password.txt'
$staffIdPath = Join-Path $localData 'local-staff-user-id.txt'
if (!(Test-Path -LiteralPath $staffIdPath)) { 'admin' | Set-Content -LiteralPath $staffIdPath }
$staffId = (Get-Content -LiteralPath $staffIdPath -Raw).Trim()
docker info --format '{{.ServerVersion}}' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Start Docker Desktop, then run this script again.' }
docker build -q -t foodbridge-api:local -f "$repoRoot/backend/Dockerfile" "$repoRoot"
if ($LASTEXITCODE -ne 0) { throw 'API container build failed' }
if (!(docker network ls --filter 'name=^foodbridge-development$' --format '{{.ID}}')) { docker network create foodbridge-development | Out-Null }
if (!(docker ps -a --filter 'name=^/foodbridge-postgres$' --format '{{.ID}}')) {
    docker run -d --name foodbridge-postgres --network foodbridge-development --restart unless-stopped -e POSTGRES_USER=foodbridge -e POSTGRES_DB=foodbridge -e "POSTGRES_PASSWORD=$dbPassword" -p 127.0.0.1:5433:5432 -v foodbridge-pg-data:/var/lib/postgresql/data postgres:17 | Out-Null
} else {
    docker start foodbridge-postgres | Out-Null
    $attached = docker network inspect foodbridge-development --format '{{range .Containers}}{{.Name}} {{end}}'
    if ($attached -notmatch 'foodbridge-postgres') { docker network connect foodbridge-development foodbridge-postgres }
    docker update --restart unless-stopped foodbridge-postgres | Out-Null
}
$databaseUrl = "postgresql+psycopg://foodbridge:$dbPassword@foodbridge-postgres:5432/foodbridge"
foreach ($name in @('foodbridge-api', 'foodbridge-maintenance')) {
    if (docker ps -a --filter "name=^/$name`$" --format '{{.ID}}') { docker rm -f $name | Out-Null }
}
docker run -d --name foodbridge-api --network foodbridge-development --restart unless-stopped -p 127.0.0.1:8000:8000 -e "DATABASE_URL=$databaseUrl" -e "ADMIN_USERNAME=$staffId" -e "ADMIN_PASSWORD=$staffPassword" -e DEMO_DATA=false -e GOOGLE_ROUTES_API_KEY -e ENVIRONMENT=production foodbridge-api:local | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'API startup failed' }
docker run -d --name foodbridge-maintenance --network foodbridge-development --restart unless-stopped -e "DATABASE_URL=$databaseUrl" -e API_READY_URL=http://foodbridge-api:8000/health/ready -e BACKUP_DIR=/backups -v foodbridge-local-backups:/backups foodbridge-api:local python -m app.scheduler | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Maintenance startup failed' }
Write-Output 'Local PostgreSQL, API and scheduled backups started.'
Write-Output 'API: http://localhost:8000/docs'
Write-Output 'Staff portal: http://localhost:8000/staff/'
Write-Output "Staff User ID: $staffId (password in backend/data/local-staff-password.txt)"
Write-Output 'Demo seeding is disabled. Import reviewed actual listings through the staff API.'
