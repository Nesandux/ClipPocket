#define MyAppName "Clip Pocket"
#define MyAppVersion "1.0.0"

[Setup]
AppId={{861DC1A4-540B-4B55-8B74-273CFAD6B029}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher=Nesandu
AppPublisherURL=https://github.com/Nesandux/ClipPocket
DefaultDirName={localappdata}\Programs\ClipPocket
DisableDirPage=no
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
WizardStyle=modern
OutputDir=installer-output
OutputBaseFilename=ClipPocket-Setup
Compression=lzma2/fast
SolidCompression=yes
UninstallDisplayIcon={app}\ClipPocket.exe
LicenseFile=..\LICENSE
CloseApplications=yes
RestartApplications=no

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Shortcuts:"; Flags: unchecked

[Files]
Source: "dist\ClipPocket\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\Clip Pocket"; Filename: "{app}\ClipPocket.exe"; WorkingDir: "{app}"
Name: "{autodesktop}\Clip Pocket"; Filename: "{app}\ClipPocket.exe"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\ClipPocket.exe"; Description: "Open Clip Pocket"; Flags: nowait postinstall skipifsilent
