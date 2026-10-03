<#
Ramu 2.5D cutout pilot runner for the NanuYT PC.

  .\run_pilot.ps1 selftest   Placeholder parts through the whole chain. Proves the
                             pipeline on this PC and gives real GPU render numbers.
  .\run_pilot.ps1 preview    Real Ramu manifest -> rig -> appearance preview. STOP here
                             for sign-off.
  .\run_pilot.ps1 test       Approved rig -> 10 s test -> QA -> render.

Every Blender call uses --python-exit-code 1, so a failed step stops the run
with a non-zero exit code (the job runner can rely on that). Renders are one
GPU job; pass -RequireIdleGpu to refuse to start while ComfyUI holds VRAM.

Written in the cloud session and not yet run on Windows. Watch the first run.
#>
param(
    [Parameter(Mandatory = $true)][ValidateSet("selftest", "preview", "test")][string]$Stage,
    [string]$Blender = "C:\Program Files\Blender Foundation\Blender 5.2\blender.exe",
    [string]$Manifest = "rig\ramu_rig.json",
    [string]$Audio = "audio\ramu_line.wav",
    [string]$Cues = "audio\ramu_line.rhubarb.json",
    [string]$Rhubarb = "",
    [ValidateSet("eevee", "cycles")][string]$Engine = "eevee",
    [switch]$RequireIdleGpu
)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

function Invoke-Blender([string[]]$BlArgs) {
    Write-Host ">> blender $($BlArgs -join ' ')"
    & $Blender -b --factory-startup --python-exit-code 1 @BlArgs
    if ($LASTEXITCODE -ne 0) { throw "Blender step failed (exit $LASTEXITCODE)" }
}

function Get-SpeechArgs {
    if (-not (Test-Path $Audio)) {
        Write-Warning "No line audio at $Audio - mouth stays at rest and mouth timing is not checked."
        return @()
    }
    if (-not (Test-Path $Cues) -and $Rhubarb -and (Test-Path $Rhubarb)) {
        & $Rhubarb -q -r phonetic -f json --extendedShapes GHX -o $Cues $Audio
        if ($LASTEXITCODE -ne 0) { throw "Rhubarb failed" }
    }
    $a = @("--audio", $Audio)
    if (Test-Path $Cues) { $a += @("--cues", $Cues) }
    else { Write-Warning "No Rhubarb cues - using the amplitude fallback (lip-flap, not phoneme-accurate)." }
    return $a
}

function Invoke-Chain([string]$OutDir, [string]$RigBlend) {
    $test = Join-Path $OutDir "ramu_test_10s.blend"
    Invoke-Blender (@($RigBlend, "-P", "scripts\animate_test.py", "--", "--shot", "shots\test_10s.json",
            "--out", $test) + @(Get-SpeechArgs))
    Invoke-Blender @($test, "-P", "scripts\qa_checks.py", "--", "--out", (Join-Path $OutDir "qa_report.json"))
    $r = @($test, "-P", "scripts\render_bench.py", "--", "--out-dir", (Join-Path $OutDir "render"), "--engine", $Engine)
    if ($RequireIdleGpu) { $r += "--require-idle-gpu" }
    Invoke-Blender $r
    Write-Host "Done. Review $OutDir\qa_report.md, $OutDir\render\render_report.json and the MP4 in $OutDir\render."
}

switch ($Stage) {
    "selftest" {
        $o = "out\selftest"
        Invoke-Blender @("-P", "tools\make_placeholder_parts.py", "--", "--out", "$o\placeholder")
        Invoke-Blender @("-P", "scripts\build_rig.py", "--", "--manifest", "$o\placeholder\rig_manifest.json", "--out", "$o\rig.blend")
        Invoke-Blender @("$o\rig.blend", "-P", "scripts\preview_appearance.py", "--", "--out", "$o\appearance_preview.png", "--engine", $Engine)
        Invoke-Chain $o "$o\rig.blend"
    }
    "preview" {
        $o = "out\ramu"
        Invoke-Blender @("-P", "scripts\build_rig.py", "--", "--manifest", $Manifest, "--out", "$o\ramu_cutout_rig.blend")
        Invoke-Blender @("$o\ramu_cutout_rig.blend", "-P", "scripts\preview_appearance.py", "--", "--out", "$o\appearance_preview.png", "--engine", $Engine)
        Write-Host "STOP: review $o\appearance_preview.png. After sign-off, set appearance_approved in $Manifest and run: .\run_pilot.ps1 preview; .\run_pilot.ps1 test"
    }
    "test" {
        Invoke-Chain "out\ramu" "out\ramu\ramu_cutout_rig.blend"
    }
}
