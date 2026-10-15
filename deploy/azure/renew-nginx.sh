#!/bin/sh
set -eu
cd /opt/danzzan/Danzzan-BE
/usr/bin/docker compose --env-file deploy/azure/secrets/frontend-build.env \
  -f docker-compose.azure.yml exec -T frontend nginx -t
/usr/bin/docker compose --env-file deploy/azure/secrets/frontend-build.env \
  -f docker-compose.azure.yml exec -T frontend nginx -s reload
