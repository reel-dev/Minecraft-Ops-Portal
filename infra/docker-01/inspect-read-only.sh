#!/bin/sh
# Read-only Docker metadata. No container exec, logs, environment or source paths.
set -eu

container=${1:-minecraft-server}
if [ "$#" -gt 1 ]; then
  printf '%s\n' 'Usage: sh inspect-read-only.sh [container-name]' >&2
  exit 2
fi
case "$container" in
  ''|[!a-zA-Z0-9]*|*[!a-zA-Z0-9_.-]*)
    printf '%s\n' 'Invalid container name.' >&2; exit 2 ;;
esac
if [ "${#container}" -gt 128 ]; then exit 2; fi
command -v docker >/dev/null 2>&1 || { printf '%s\n' 'Docker CLI missing.' >&2; exit 1; }
command -v timeout >/dev/null 2>&1 || { printf '%s\n' 'timeout command missing; nothing queried.' >&2; exit 1; }

# Select metadata in Docker, rather than fetching a full inspect with secrets.
format='Container: {{.Name}}
Image config ID (NOT repository manifest digest): {{.Image}}
State: {{.State.Status}}
Running: {{.State.Running}}
Paused: {{.State.Paused}}
Restarting: {{.State.Restarting}}
OOMKilled: {{.State.OOMKilled}}
Restart policy: {{.HostConfig.RestartPolicy.Name}}
Memory limit bytes (0 = unset): {{.HostConfig.Memory}}
Healthcheck configured: {{if .Config.Healthcheck}}true{{else}}false{{end}}
Mounts (source paths and volume names omitted):
{{range .Mounts}}  Type={{.Type}} DataTarget={{if eq .Destination "/data"}}true{{else}}false{{end}} ReadWrite={{.RW}}
{{end}}Published ports (host IP omitted; inspect exposure separately):
{{range $port, $bindings := .NetworkSettings.Ports}}{{range $bindings}}  ContainerPort={{$port}} HostPort={{.HostPort}}
{{end}}{{end}}'

if ! report=$(timeout 15 docker container inspect --format "$format" -- "$container" 2>/dev/null); then
  printf '%s\n' 'Container inspection failed or timed out. No changes made.' >&2
  exit 1
fi
printf '%s\n' 'Elbracht SMP Control - read-only inventory v1'
printf 'Captured UTC: %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
printf '%s\n' "$report"
printf '%s\n' 'Not verified: Minecraft/Fabric/Java runtime, installed mods, backup/restore, manifest digest, network access.'
