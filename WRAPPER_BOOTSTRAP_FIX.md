# Wrapper bootstrap fix

Root cause fixed: `BOOTSTRAP_GRADLE_WINDOWS.ps1` assigned to PowerShell's automatic/read-only `$HOME` variable.

Changed:
- `$home` -> `$gradleHome`
- all references updated
- scripts scanned for assignments to common PowerShell automatic variables; none remain

Verification boundary:
- Script/static inspection: VERIFIED
- Windows PowerShell execution: UNVERIFIED in this environment
- Android build: UNVERIFIED
