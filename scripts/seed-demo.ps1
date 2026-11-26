<#
  Seeds a realistic demo workspace through the public API (no database access needed).
  Safe to re-run: existing users and projects are reused, and tasks are only created once.

  Usage (API must be running):
    .\scripts\seed-demo.ps1
    .\scripts\seed-demo.ps1 -BaseUrl http://127.0.0.1:8080

  Demo logins (all use $DemoPassword, a throwaway local-only value):
    priya@acme-demo.test   (admin)
    rahul@acme-demo.test, ananya@acme-demo.test, karthik@acme-demo.test, meera@acme-demo.test (members)
#>
param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$DemoPassword = "Demo-Workspace-2026"
)

$ErrorActionPreference = "Stop"

function Invoke-Api($Method, $Path, $Body, $Token) {
    $tmp = $null
    $curlArgs = @('-s', '-i', '-X', $Method, "$BaseUrl$Path", '-H', 'Content-Type: application/json')
    if ($Token) { $curlArgs += @('-H', "Authorization: Bearer $Token") }
    if ($null -ne $Body) {
        $tmp = [IO.Path]::GetTempFileName()
        [IO.File]::WriteAllText($tmp, ($Body | ConvertTo-Json -Compress))
        $curlArgs += @('--data-binary', "@$tmp")
    }
    $raw = (& curl.exe @curlArgs) -join "`n"
    if ($tmp) { Remove-Item $tmp }
    $code = [int]([regex]::Match($raw, '^HTTP/\S+ (\d+)').Groups[1].Value)
    $total = [regex]::Match($raw, '(?im)^X-Total-Count:\s*(\d+)').Groups[1].Value
    $parts = [regex]::Split($raw, '\r?\n\r?\n', 2)
    $text = if ($parts.Count -gt 1) { $parts[1] } else { '' }
    $json = $null
    if ($text) { try { $json = $text | ConvertFrom-Json } catch { } }
    [pscustomobject]@{ Code = $code; Json = $json; Total = $total; Text = $text }
}

function Assert-Ok($Result, $What) {
    if ($Result.Code -lt 200 -or $Result.Code -ge 300) {
        throw "$What failed (HTTP $($Result.Code)): $($Result.Text)"
    }
}

function Day($offset) { (Get-Date).AddDays($offset).ToString('yyyy-MM-dd') }

if ((Invoke-Api GET '/actuator/health').Code -ne 200) {
    throw "The API is not reachable at $BaseUrl. Start it first (see README)."
}

# ---- Workspace and admin
$register = Invoke-Api POST '/api/v1/auth/register-tenant' @{
    tenantName = 'Acme Studio'; tenantSlug = 'acme-studio'
    firstName = 'Priya'; lastName = 'Sharma'; email = 'priya@acme-demo.test'; password = $DemoPassword
}
if ($register.Code -eq 201) { Write-Host "Created workspace 'Acme Studio'." }
elseif ($register.Code -eq 409) { Write-Host "Workspace already exists, reusing it." }
else { Assert-Ok $register 'Workspace registration' }

function Get-Token($Email) {
    $login = Invoke-Api POST '/api/v1/auth/login' @{ email = $Email; password = $DemoPassword }
    Assert-Ok $login "Login for $Email"
    $login.Json.accessToken
}

$adminToken = Get-Token 'priya@acme-demo.test'

# ---- Team
$teamSpec = @(
    @{ key = 'Rahul';   first = 'Rahul';   last = 'Verma'; email = 'rahul@acme-demo.test' },
    @{ key = 'Ananya';  first = 'Ananya';  last = 'Iyer';  email = 'ananya@acme-demo.test' },
    @{ key = 'Karthik'; first = 'Karthik'; last = 'Rao';   email = 'karthik@acme-demo.test' },
    @{ key = 'Meera';   first = 'Meera';   last = 'Nair';  email = 'meera@acme-demo.test' }
)
$tokens = @{ Priya = $adminToken }
$ids = @{}
$me = Invoke-Api GET '/api/v1/auth/me' $null $adminToken
$ids['Priya'] = $me.Json.userId
foreach ($member in $teamSpec) {
    $created = Invoke-Api POST '/api/v1/users' @{
        firstName = $member.first; lastName = $member.last; email = $member.email; password = $DemoPassword
    } $adminToken
    if ($created.Code -ne 201 -and $created.Code -ne 409) { Assert-Ok $created "Creating $($member.email)" }
    $tokens[$member.key] = Get-Token $member.email
    $ids[$member.key] = (Invoke-Api GET '/api/v1/auth/me' $null $tokens[$member.key]).Json.userId
}
Write-Host "Team ready: $($ids.Count) people."

# ---- Projects
$projectSpec = @(
    @{ name = 'Website Redesign';        description = 'Refresh the marketing site: new homepage, pricing page and blog.' },
    @{ name = 'Mobile App Launch';       description = 'Ship v1.0 of the iOS and Android apps.' },
    @{ name = 'Customer Onboarding';     description = 'Reduce time-to-first-value for new customers.' },
    @{ name = 'Q4 Marketing Campaign';   description = 'Campaign planning, assets and reporting for Q4.' },
    @{ name = 'Legacy Migration';        description = 'Move the old reporting service to the new platform (completed).' }
)
$existing = (Invoke-Api GET '/api/v1/projects?size=100' $null $adminToken).Json
$projects = @{}
foreach ($spec in $projectSpec) {
    $found = $existing | Where-Object { $_.name -eq $spec.name } | Select-Object -First 1
    if ($found) { $projects[$spec.name] = $found.id; continue }
    $created = Invoke-Api POST '/api/v1/projects' @{ name = $spec.name; description = $spec.description } $adminToken
    Assert-Ok $created "Creating project $($spec.name)"
    $projects[$spec.name] = $created.Json.id
}
Write-Host "Projects ready: $($projects.Count)."

# ---- Tasks (only when the workspace has none yet)
$taskCount = (Invoke-Api GET '/api/v1/tasks?size=1' $null $adminToken).Total
if ([int]$taskCount -gt 0) {
    Write-Host "Workspace already has $taskCount tasks - skipping task and comment seeding."
}
else {
    # creator = who creates it (so members can later edit their own tasks)
    $tasks = @(
        @{ p = 'Website Redesign'; t = 'Audit current site analytics'; pr = 'MEDIUM'; due = -12; a = 'Ananya'; s = 'DONE'; by = 'Priya' },
        @{ p = 'Website Redesign'; t = 'Design new homepage wireframes'; pr = 'HIGH'; due = -2; a = 'Rahul'; s = 'IN_PROGRESS'; by = 'Priya'; d = 'Three layout options for stakeholder review.' },
        @{ p = 'Website Redesign'; t = 'Write pricing page copy'; pr = 'MEDIUM'; due = 4; a = 'Meera'; s = 'TODO'; by = 'Priya' },
        @{ p = 'Website Redesign'; t = 'Set up blog CMS'; pr = 'LOW'; due = 14; a = 'Karthik'; s = 'TODO'; by = 'Karthik' },
        @{ p = 'Website Redesign'; t = 'Accessibility review (WCAG AA)'; pr = 'HIGH'; due = 9; a = 'Ananya'; s = 'TODO'; by = 'Priya' },
        @{ p = 'Mobile App Launch'; t = 'Finalize onboarding flow'; pr = 'HIGH'; due = 1; a = 'Rahul'; s = 'IN_PROGRESS'; by = 'Rahul'; d = 'Three screens, skippable, with progress dots.' },
        @{ p = 'Mobile App Launch'; t = 'Fix crash on Android 12 login'; pr = 'HIGH'; due = -1; a = 'Karthik'; s = 'IN_PROGRESS'; by = 'Priya' },
        @{ p = 'Mobile App Launch'; t = 'Prepare App Store screenshots'; pr = 'MEDIUM'; due = 7; a = 'Meera'; s = 'TODO'; by = 'Meera' },
        @{ p = 'Mobile App Launch'; t = 'Push notification opt-in copy'; pr = 'LOW'; due = 10; a = $null; s = 'TODO'; by = 'Priya' },
        @{ p = 'Mobile App Launch'; t = 'Beta feedback triage'; pr = 'MEDIUM'; due = -6; a = 'Ananya'; s = 'DONE'; by = 'Priya' },
        @{ p = 'Customer Onboarding'; t = 'Draft welcome email sequence'; pr = 'MEDIUM'; due = 3; a = 'Meera'; s = 'IN_PROGRESS'; by = 'Priya' },
        @{ p = 'Customer Onboarding'; t = 'Record product walkthrough video'; pr = 'MEDIUM'; due = 12; a = 'Ananya'; s = 'TODO'; by = 'Priya' },
        @{ p = 'Customer Onboarding'; t = 'Create in-app checklist'; pr = 'HIGH'; due = 6; a = 'Rahul'; s = 'TODO'; by = 'Rahul' },
        @{ p = 'Customer Onboarding'; t = 'Survey first-week customers'; pr = 'LOW'; due = -4; a = 'Karthik'; s = 'DONE'; by = 'Karthik' },
        @{ p = 'Q4 Marketing Campaign'; t = 'Define campaign goals and KPIs'; pr = 'HIGH'; due = -9; a = 'Priya'; s = 'DONE'; by = 'Priya' },
        @{ p = 'Q4 Marketing Campaign'; t = 'Produce social media assets'; pr = 'MEDIUM'; due = 5; a = 'Meera'; s = 'IN_PROGRESS'; by = 'Meera' },
        @{ p = 'Q4 Marketing Campaign'; t = 'Book newsletter sponsorship'; pr = 'LOW'; due = 20; a = $null; s = 'TODO'; by = 'Priya' },
        @{ p = 'Q4 Marketing Campaign'; t = 'Weekly performance report'; pr = 'MEDIUM'; due = 2; a = 'Priya'; s = 'TODO'; by = 'Priya' },
        @{ p = 'Legacy Migration'; t = 'Migrate reporting database'; pr = 'HIGH'; due = -30; a = 'Karthik'; s = 'DONE'; by = 'Priya' },
        @{ p = 'Legacy Migration'; t = 'Decommission old server'; pr = 'MEDIUM'; due = -20; a = 'Karthik'; s = 'DONE'; by = 'Priya' }
    )
    $taskIds = @{}
    foreach ($spec in $tasks) {
        $body = @{ projectId = $projects[$spec.p]; title = $spec.t; priority = $spec.pr; dueDate = (Day $spec.due) }
        if ($spec.a) { $body.assigneeId = $ids[$spec.a] }
        if ($spec.d) { $body.description = $spec.d }
        $created = Invoke-Api POST '/api/v1/tasks' $body $tokens[$spec.by]
        Assert-Ok $created "Creating task '$($spec.t)'"
        $taskIds[$spec.t] = $created.Json.id
        if ($spec.s -ne 'TODO') {
            Assert-Ok (Invoke-Api PATCH "/api/v1/tasks/$($created.Json.id)/status" @{ status = $spec.s } $adminToken) "Status of '$($spec.t)'"
        }
    }
    Write-Host "Created $($tasks.Count) tasks."

    $comments = @(
        @{ t = 'Design new homepage wireframes'; by = 'Rahul'; c = 'First two options are ready in Figma. Third one lands tomorrow.' },
        @{ t = 'Design new homepage wireframes'; by = 'Priya'; c = 'Great. Please make the pricing teaser more prominent in option B.' },
        @{ t = 'Design new homepage wireframes'; by = 'Ananya'; c = 'Heads up: option A fails contrast on the hero buttons.' },
        @{ t = 'Fix crash on Android 12 login'; by = 'Karthik'; c = 'Reproduced. It is the new biometric prompt - patching now.' },
        @{ t = 'Fix crash on Android 12 login'; by = 'Priya'; c = 'This blocks the release. Please prioritise.' },
        @{ t = 'Finalize onboarding flow'; by = 'Meera'; c = 'Copy for all three screens is in the doc.' },
        @{ t = 'Draft welcome email sequence'; by = 'Priya'; c = 'Keep it under 120 words per email.' },
        @{ t = 'Produce social media assets'; by = 'Karthik'; c = 'Do we need the 9:16 versions as well?' },
        @{ t = 'Produce social media assets'; by = 'Meera'; c = 'Yes - stories and reels. I will export them Friday.' }
    )
    foreach ($comment in $comments) {
        Assert-Ok (Invoke-Api POST "/api/v1/tasks/$($taskIds[$comment.t])/comments" @{ body = $comment.c } $tokens[$comment.by]) "Comment on '$($comment.t)'"
    }
    Write-Host "Added $($comments.Count) comments."

    Assert-Ok (Invoke-Api PATCH "/api/v1/projects/$($projects['Legacy Migration'])/archive" $null $adminToken) 'Archiving Legacy Migration'
}

Write-Host ""
Write-Host "Demo data ready. Sign in at http://localhost:5173 as:"
Write-Host "  Admin : priya@acme-demo.test"
Write-Host "  Member: rahul@acme-demo.test  (also ananya, karthik, meera @acme-demo.test)"
Write-Host "  Password: see the DemoPassword value at the top of scripts\seed-demo.ps1"