@echo off
REG ADD "HKEY_CLASSES_ROOT\dwnld" /ve /d "URL:Dwnld Protocol" /f
REG ADD "HKEY_CLASSES_ROOT\dwnld" /v "URL Protocol" /f
REG ADD "HKEY_CLASSES_ROOT\dwnld\shell\open\command" /ve /d "\"%~dp0at.dwnld.exe\" \"%1\"" /f
