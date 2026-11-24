# Evidencia de la Etapa 0: preparación del entorno

La consigna pide una captura del instalador finalizado y otra de la lista de harnesses instalados.

| Captura | Qué muestra |
|---|---|
| [instalador-modo-full.png](instalador-modo-full.png) | Active Stack v0.8.1 instalado para Claude Code en **modo Full** (`"mode": "full"`), con las 14 skills que gestiona el instalador. El manifiesto de instalación es del **2026-11-20 11:22**. Abajo, las versiones de las herramientas previas: Go, Git, Node, Claude Code y OpenSpec. |
| [harnesses-instalados.png](harnesses-instalados.png) | Lo que instaló Active Stack: un respaldo por cada componente (orquestador SDD, Engram, Context7, Playwright, design-mockups y permisos, para Claude Code y OpenCode), los servidores MCP activos en Claude Code (`context7`, `engram`, `playwright`), la versión de Engram y las skills disponibles en `~/.claude/skills`. |

Al lado de cada imagen está el `.txt` con la misma salida en texto plano.

## Cómo se tomaron

- **Fecha:** las capturas se tomaron el 2026-11-24, sobre la instalación que se hizo el 2026-11-20. No se volvió a correr el instalador, para no pisar la configuración del entorno con el que se desarrolló el proyecto.
- **Comandos:** son de solo lectura y se corrieron en Git Bash. Cada captura muestra el comando exacto (`$ ...`) y su salida real, sin editar.
- **Única modificación:** la ruta del perfil de usuario se reemplazó por `%USERPROFILE%` o `~`, para no publicar el nombre de usuario de la máquina.

## Cómo reproducirlo

```bash
active-stack version
cat ~/.active-stack/installation.json
ls ~/.active-stack/backups
ls ~/.claude/skills
```
