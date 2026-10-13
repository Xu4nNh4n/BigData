param(
    [string]$Date = '2026-10-08',
    [string]$DrillUrl = 'http://localhost:8047'
)

$ErrorActionPreference = 'Stop'
$parsedDate = [datetime]::ParseExact($Date, 'yyyy-MM-dd', $null)
$tableDate = $parsedDate.ToString('yyyy_MM_dd')
$sourceFile = ('analytics.cassandra.`events-DATE.json`' -replace 'DATE', $Date)
$projectRoot = Split-Path $PSScriptRoot -Parent
$sourcePath = Join-Path $projectRoot "analytics/cassandra/events-$Date.json"
$curatedPath = Join-Path $projectRoot 'analytics/curated'
if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) { throw "Missing JSON export for date $Date" }
$sourceHash = (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash.ToLowerInvariant()
# Build a new version; publish the manifest only after CTAS completes. Existing readers keep their table.
$tableName = "events_${tableDate}_$([guid]::NewGuid().ToString('N'))"
$targetTable = "analytics.curated.$tableName"
$manifestPath = Join-Path $curatedPath "events_$tableDate.ready.json"

function Invoke-DrillSql([string]$Sql, [int]$AutoLimit = 100) {
    $body = @{ queryType = 'SQL'; query = $Sql; autoLimit = $AutoLimit } |
        ConvertTo-Json -Depth 5
    $result = Invoke-RestMethod -Uri "$DrillUrl/query.json" -Method Post `
        -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
    if ($result.queryState -ne 'COMPLETED' -or $result.errorMessage) {
        $errorText = if ($result.errorMessage) { $result.errorMessage } else { $result | ConvertTo-Json -Depth 5 }
        throw "Drill query failed: $errorText"
    }
    return $result
}

# CTAS needs a writable workspace and deterministic compression.
Invoke-DrillSql "ALTER SYSTEM SET store.parquet.compression = 'snappy'" 0 | Out-Null

$createSql = @'
CREATE TABLE TARGET_TABLE AS
SELECT CAST(event_date AS DATE) AS event_date,
       CAST(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', '') AS TIMESTAMP) AS event_time,
       CAST(event_id AS VARCHAR) AS event_id,
       CAST(event_type AS VARCHAR) AS event_type,
       CAST(order_id AS BIGINT) AS order_id,
       CAST(product_sku AS VARCHAR) AS product_sku,
       CAST(quantity AS INT) AS quantity,
       CAST(amount AS DECIMAL(18, 2)) AS amount,
       CAST(payment_method AS VARCHAR) AS payment_method,
       CAST(status AS VARCHAR) AS status,
       CAST(`metadata` AS VARCHAR) AS `metadata`
FROM (
    SELECT *, ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM SOURCE_FILE
) raw
WHERE event_rank = 1 AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
  AND event_type IS NOT NULL
'@
$createSql = $createSql.Replace('TARGET_TABLE', $targetTable).Replace('SOURCE_FILE', $sourceFile)
Invoke-DrillSql $createSql 0 | Out-Null

if ((Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $sourceHash) {
    throw 'JSON changed during conversion; Parquet was not published. Run this script again for the latest export.'
}
$tablePath = Join-Path $curatedPath $tableName
if (-not (Test-Path -LiteralPath $tablePath -PathType Container)) {
    throw 'Parquet table not found on host; check mount ./analytics:/analytics and analytics.curated workspace.'
}
$manifest = @{
    table = $tableName
    sourceSha256 = $sourceHash
    date = $Date
    builtAt = [datetime]::UtcNow.ToString('o')
    format = 'PARQUET'
    compression = 'SNAPPY'
}
$temporaryManifest = Join-Path $curatedPath "$tableName.manifest.tmp"
try {
    [IO.File]::WriteAllText($temporaryManifest, ($manifest | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $temporaryManifest -Destination $manifestPath -Force
} finally {
    if (Test-Path -LiteralPath $temporaryManifest) { Remove-Item -LiteralPath $temporaryManifest }
}

[pscustomobject]@{
    Source = $sourceFile
    Target = $targetTable
    Format = 'Parquet/Snappy'
    Manifest = $manifestPath
    SourceSha256 = $sourceHash
} | ConvertTo-Json -Depth 8
