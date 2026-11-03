; Compile with scripts/ci/package-windows.ps1 using an existing release JAR.
#ifndef AppVersion
  #error AppVersion is required
#endif
#ifndef FileVersion
  #error FileVersion is required
#endif
#ifndef StageDir
  #error StageDir is required
#endif

[Setup]
; Keep this ID stable so later installers upgrade the same application.
AppId={{671A7A06-B52B-4370-A8AB-E8C95BA7B70C}
AppName=FdSwarm
AppVersion={#AppVersion}
VersionInfoVersion={#FileVersion}
AppPublisher=Dick Lieber, WA9NNN
AppPublisherURL=https://github.com/dicklieber/fdswarm
DefaultDirName={localappdata}\Programs\FdSwarm
DefaultGroupName=FdSwarm
PrivilegesRequired=lowest
; Windows 11 ARM can run the bundled x64 Java under emulation.
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
OutputBaseFilename=FdSwarm-{#AppVersion}-windows-x64-setup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
DisableProgramGroupPage=yes
UninstallDisplayName=FdSwarm {#AppVersion}
UninstallDisplayIcon={app}\runtime\bin\javaw.exe
CloseApplications=yes
RestartApplications=no

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Shortcuts:"; Flags: unchecked

[InstallDelete]
; Replace the entire private runtime so removed Java files cannot survive an upgrade.
Type: filesandordirs; Name: "{app}\runtime"

[Files]
Source: "{#StageDir}\lib\fdswarm.jar"; DestDir: "{app}\lib"; Flags: ignoreversion
Source: "{#StageDir}\runtime\*"; DestDir: "{app}\runtime"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#StageDir}\LICENSE"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\FdSwarm"; Filename: "{app}\runtime\bin\javaw.exe"; Parameters: "-jar ""{app}\lib\fdswarm.jar"""; WorkingDir: "{app}"
Name: "{autodesktop}\FdSwarm"; Filename: "{app}\runtime\bin\javaw.exe"; Parameters: "-jar ""{app}\lib\fdswarm.jar"""; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\runtime\bin\javaw.exe"; Parameters: "-jar ""{app}\lib\fdswarm.jar"""; WorkingDir: "{app}"; Description: "Start FdSwarm"; Flags: nowait postinstall skipifsilent

; Never delete %LOCALAPPDATA%\FdSwarm: it contains the user's logs and configuration.
