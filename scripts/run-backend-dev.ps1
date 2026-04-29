$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$envFile = Join-Path $root ".env"

if (-not (Test-Path $envFile)) {
    throw "未找到 $envFile，请先复制 .env.example 为 .env 并填写本地配置。"
}

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if (-not $line -or $line.StartsWith("#") -or -not $line.Contains("=")) {
        return
    }

    $name, $value = $line -split "=", 2
    $name = $name.Trim()
    $value = $value.Trim().Trim('"').Trim("'")

    if ($name) {
        Set-Item -Path "Env:$name" -Value $value
    }
}

Push-Location (Join-Path $root "LiuTech")
try {
    mvn spring-boot:run
}
finally {
    Pop-Location
}
