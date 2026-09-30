param(
    [Parameter(Mandatory = $true)][ValidateSet('main', 'ai')][string]$Database,
    [switch]$Baseline
)
$ErrorActionPreference = 'Stop'
if (-not $env:FLYWAY_URL -or -not $env:FLYWAY_USER) {
    throw '请先设置 FLYWAY_URL、FLYWAY_USER、FLYWAY_PASSWORD（独立迁移账号）；不要使用应用账号执行 DDL。'
}
if (-not $env:JAVA_HOME) { throw '请先把 JAVA_HOME 指向 JDK 21。' }
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    # URL 和密码由 Flyway 从环境变量读取，避免批处理层 & 转义或出现在命令行。
    $migrationGoals = @('flyway:migrate', 'flyway:validate', 'flyway:info')
    if ($Baseline) { $migrationGoals = @('flyway:baseline') + $migrationGoals }
    & mvn -f Docs/SQL/migrations/pom.xml "-Dmigration.database=$Database" @migrationGoals
    if ($LASTEXITCODE -ne 0) { throw "数据库迁移失败（exit=$LASTEXITCODE），不能继续发布应用。" }
} finally { Pop-Location }
