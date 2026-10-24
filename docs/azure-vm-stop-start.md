# Azure 시연 VM 중지·재시작 안내

> 공개 문서의 `${...}`는 실제 값 대신 사용하는 변수입니다. 실행 전 [비공개 운영 값 사용 안내](azure-operations-values.md)를 읽고 본인 환경의 값을 설정하세요.

대상: `${VM_NAME}`, 리소스 그룹 `${AZURE_RESOURCE_GROUP}`.
서비스 주소: **https://danzzan.koreacentral.cloudapp.azure.com**.

이 문서는 VM 컴퓨팅 비용을 멈추면서 기존 주소·데이터·배포 이미지를 보존하는 절차다. VM이나 리소스 그룹을 삭제하는 절차가 아니다.

## 보존 대상과 비용

| 리소스 | 처리 | 이유 |
| --- | --- | --- |
| VM | 사용하지 않을 때 할당 취소 | 컴퓨팅 과금 중지 |
| `${PUBLIC_IP_NAME}` | 유지 | 정적 IP `${VM_PUBLIC_IP}`와 DNS label `danzzan` 보존 |
| `${OS_DISK_NAME}` | 유지 | 배포 파일·설정·인증서·MySQL/Redis/Kafka 영구 볼륨 보존 |
| `danzzandemo2026` | 유지 | 비공개 `images` Blob 보존 |
| NIC·VNet·NSG·관리 ID | 유지 | 기존 접속·내부 네트워크·Blob 접근 권한 보존 |

할당 취소 중에는 웹사이트·SSH·VM 내부 DB에 접속할 수 없다. 디스크·공인 IP·Blob 등 보관 리소스 요금은 남는다. 단순 OS 종료나 Docker 종료로 컴퓨팅 과금 중지를 판단하지 말고 Azure 상태가 **Stopped (deallocated)**인지 확인한다.

## 다시 시작하기

Azure Portal에서 해당 VM → **시작**을 누른다. CLI로 실행하려면 같은 구독에 로그인한 후 아래 명령을 사용한다. CLI가 끝나도 애플리케이션 준비 시간이 더 필요할 수 있다.

```bash
: "${AZURE_RESOURCE_GROUP:?Set AZURE_RESOURCE_GROUP from the private operations guide}"
: "${AZURE_SUBSCRIPTION_ID:?Set AZURE_SUBSCRIPTION_ID from the private operations guide}"
: "${VM_NAME:?Set VM_NAME from the private operations guide}"
az vm start \
  --subscription "${AZURE_SUBSCRIPTION_ID}" \
  --resource-group "${AZURE_RESOURCE_GROUP}" \
  --name "${VM_NAME}"
```

Docker 서비스는 부팅 시 자동 시작하도록 설정되어 있다. frontend·app·mysql·redis·kafka는 `restart: unless-stopped`를 사용한다. 단발 초기화용 kafka-init의 종료 코드 0은 정상이다. 단, 컨테이너를 별도로 수동 중지해두면 `unless-stopped` 때문에 다음 부팅에서 자동 시작하지 않을 수 있으므로 이 절차에서는 컨테이너를 개별적으로 중지하지 않는다.

1. Azure 상태가 Running인지 확인한다.
2. 기존 주소의 `/api/health`가 성공하는지 확인한다.
3. 홈·공지·관리자 로그인 페이지와 홈 이미지를 조회한다. 비공개 이미지 URL의 SAS는 만료되므로 이전 URL을 재사용하지 말고 페이지를 새로고침해 새 URL을 받는다.
4. 로그인·지도·티켓 등 실제 시연 흐름을 확인한다. SMS·AI 생성은 비용과 데이터 쓰기가 수반될 수 있어 단순 기동 확인과 구분한다.

```bash
curl --fail --show-error https://danzzan.koreacentral.cloudapp.azure.com/api/health
```

SSH 연결이 timeout이면 현재 작업자 공인 IP와 NSG 허용 범위를 확인한다. URL 변경이나 DB 공개로 해결하지 않는다. Azure Portal의 **실행 명령 → RunShellScript**에서도 다음 읽기 전용 점검을 할 수 있다.

```bash
systemctl is-enabled docker
systemctl is-active docker
docker ps -a --format '{{.Names}} {{.Status}}'
docker exec danzzan-azure-frontend-1 nginx -t
```

컨테이너나 앱이 준비되지 않으면 오류를 먼저 확인한다. 환경 파일 전체, 인증 헤더, SAS URL, 비밀번호가 포함된 로그를 공유하지 않는다. `docker compose down -v`, `docker volume rm`, VM·디스크·공인 IP 삭제는 사용하지 않는다. DB 터널의 컨테이너 IP는 재생성 시 바뀔 수 있으므로 [DB 연결 안내](azure-db-vscode-connection.md)를 참고한다.

시연 당일 처음 시작하지 않고 11월 25일경 미리 기동·점검한다. 할당 취소한 VM은 재시작 시 Azure 자원 확보가 필요하며, 용량 부족 시 시작이 지연될 수 있다.

## 사용 후 다시 비용 줄이기

작업자들이 사용을 마쳤는지 확인하고 VM을 할당 취소한다. 디스크·공인 IP·Blob은 유지한다.

```bash
: "${AZURE_RESOURCE_GROUP:?Set AZURE_RESOURCE_GROUP from the private operations guide}"
: "${AZURE_SUBSCRIPTION_ID:?Set AZURE_SUBSCRIPTION_ID from the private operations guide}"
: "${VM_NAME:?Set VM_NAME from the private operations guide}"
az vm deallocate \
  --subscription "${AZURE_SUBSCRIPTION_ID}" \
  --resource-group "${AZURE_RESOURCE_GROUP}" \
  --name "${VM_NAME}"

az vm get-instance-view \
  --subscription "${AZURE_SUBSCRIPTION_ID}" \
  --resource-group "${AZURE_RESOURCE_GROUP}" \
  --name "${VM_NAME}" \
  --query 'instanceView.statuses[].code' -o tsv
```

출력에 `PowerState/deallocated`가 있어야 한다. 자동 재시작이나 자동 종료 예약을 의미하는 절차는 아니다.

## 2026-10-22 점검 기록

- 녹화 종료 후 사용자가 비용 절감을 위한 종료와 향후 같은 URL의 정상 복구를 요청했다.
- 실제 Azure 리소스에서 Standard 정적 공인 IP와 DNS가 위 값임을 확인했다.
- VM에서 Docker enabled/active와 주요 5개 컨테이너의 자동 재시작 정책을 확인했다.
- MySQL·Redis·Kafka 데이터 볼륨이 `/var/lib/docker/volumes/`에 있고 해당 파일시스템은 OS 영구 디스크 `/dev/sda1`임을 확인했다.
- HTTPS 인증서 만료일은 2027-01-12로 확인했다.
- 사전 백업: `${BACKUP_20261022_DEALLOCATE_RESTART_DIR}/` (디렉터리 700, 파일은 root 전용). `databases.sql.gz`는 현재 DB와 이전 시연 DB의 일관된 덤프이며 gzip 무결성 검사를 통과했다. `config-private.tar.gz`에는 비공개 설정·인증서가 포함되어 있으므로 Git이나 공개 자료에 넣지 않는다.
- `containers.before.json`, `config-hashes.before.json`, `content-hash.before`는 복구 전후 비교용이다. 기존 DB·Blob 콘텐츠를 수정하거나 공개하지 않았다.

### 실제 할당 취소 후 재시작 시험 결과

- 첫 할당 취소 후 `PowerState/deallocated`를 확인하고 Azure 시작 명령으로 재기동했다. 재배포나 컨테이너 수동 시작 없이 주요 서비스가 자동으로 복구됐다.
- app·mysql·redis·kafka는 healthy, frontend는 running이었다. Nginx 설정 검사, Redis PONG, Kafka 기존 topic 목록 조회가 성공했다.
- 주요 5개 컨테이너의 ID·실행 이미지·마운트·재시작 정책이 모두 기존과 동일했다. Compose·비공개 설정·인증서 해시도 그대로였다.
- 기존 20개 콘텐츠 테이블의 정규화 데이터 해시가 재시작 전후 동일했다. 두 DB 백업을 복원할 필요는 없었다.
- 고정 IP `${VM_PUBLIC_IP}`, DNS `danzzan.koreacentral.cloudapp.azure.com` 유지 확인. 외부에서도 같은 주소의 `/api/health` 응답 `{"status":"ok"}`를 확인했다.
- HTTPS `/`, `/notice`, `/admin/login`, `/admin/theme`, `/ticket/signup`, `/api/health`, `/api/festival/settings`, `/api/home/1/images` 모두 200, API 응답 JSON 파싱 성공. 홈 이미지 API가 반환한 새 SAS URL로 비공개 Blob 이미지 1개의 실제 바이트를 읽는 데 성공했다. URL이나 SAS 서명은 기록하지 않았다.
- 이번 검증은 자동 기동·조회·이미지 읽기까지다. 로그인 제출, 발권, SMS·유료 AI 생성, 관리자 저장은 실행하지 않았다. Kafka는 기존 topic 조회와 health를 확인했으며 신규 발권 메시지를 생성하지 않았다.
- 기록 파일: 백업 디렉터리의 `restart-verification.json`, `content-hash.after`, `restart-verified`. 전체 DB 덤프·비공개 설정 백업은 VM 내부에만 보관했다.
- SSH 22 접속은 당시 작업 환경에서 timeout이 발생했다. NSG를 변경하지 않고 Azure Run Command로 점검했다. 이 제한은 웹 서비스 복구와 별개이며 동료 DB 터널은 허용된 IP에서 다시 연결해야 한다.

### 최종 상태

2026-10-22 재시작 검증 후 다시 할당 취소했다. Azure에서 `ProvisioningState/succeeded`, **`PowerState/deallocated`**를 확인했다. 최종 중지 이후에도 공인 IP는 Static `${VM_PUBLIC_IP}`, DNS는 `danzzan.koreacentral.cloudapp.azure.com`으로 유지됐다.

현재 사이트와 DB 접속 중단은 의도한 상태다. VM 컴퓨팅 과금은 멈추지만 보존한 디스크·공인 IP·Blob 요금은 남는다. 자동 시작 예약은 만들지 않았으므로 필요할 때 VM을 시작해야 한다. VM과 보존 리소스는 삭제하지 않았다. VM 전원 상태만 변경했으며 DNS·NSG·관리 ID 설정은 유지했다.
