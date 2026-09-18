@echo off
rem pnpm shim - calls the same corepack dist/pnpm.js entry point as the
rem pnpm.CMD that "corepack enable --install-directory" installs.
rem Kept as a fallback because C:\Program Files\nodejs is not writable
rem without administrator rights.
@"C:\Program Files\nodejs\node.exe" "C:\Program Files\nodejs\node_modules\corepack\dist\pnpm.js" %*
