#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f .env ]]; then
  while IFS='=' read -r clave valor; do
    case "$clave" in
      CLOUD_USER|CLOUD_PASSWORD|OAUTH_ADMIN_SECRET|OAUTH_READER_SECRET|ADMIN_USER|ADMIN_PASSWORD|READER_USER|READER_PASSWORD)
        export "$clave=${valor%$'\r'}" ;;
    esac
  done < .env
fi
./mvnw clean verify
mkdir -p logs
for nombre in config-server discovery-server auth-server cuentas-service; do
  puerto=8888
  extra=()
  if [[ "$nombre" == discovery-server ]]; then puerto=8761; fi
  if [[ "$nombre" == auth-server ]]; then puerto=9000; fi
  if [[ "$nombre" == cuentas-service ]]; then puerto=8081; extra=(--spring.profiles.active=demo,local-jms); fi
  if curl -s --max-time 2 "http://localhost:$puerto/actuator/health" >/dev/null; then echo "Puerto $puerto ocupado"; exit 1; fi
  nohup java -jar "$PWD/$nombre/target/$nombre-2.0.0.jar" "${extra[@]}" > "logs/$nombre.log" 2>&1 &
  proceso=$!
  echo "$proceso" > "logs/$nombre.pid"
  listo=false
  for intento in {1..90}; do
    if ! kill -0 "$proceso" 2>/dev/null; then echo "$nombre se cerró. Revisa logs/$nombre.log"; exit 1; fi
    if curl -fsS --max-time 2 "http://localhost:$puerto/actuator/health" 2>/dev/null | python3 -c 'import sys,json;sys.exit(0 if json.load(sys.stdin)["status"]=="UP" else 1)' 2>/dev/null; then listo=true; break; fi
    sleep 2
  done
  if [[ "$listo" != true ]]; then echo "No inició $nombre"; exit 1; fi
  echo "$nombre listo en $puerto"
done
