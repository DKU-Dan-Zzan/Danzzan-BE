# Azure VM·네트워크·관리 ID 생성 기록

> 공개 문서의 `${...}`는 실제 값 대신 사용하는 변수입니다. 실행 전 [비공개 운영 값 사용 안내](azure-operations-values.md)를 읽고 본인 환경의 값을 설정하세요.

확인일: **2026-10-14 KST**. 사용자가 기존 학생 구독에 아래 구성의 생성을 승인한 후 실행했다. 인프라 생성과 VM 관리 ID 인증 검증을 마친 뒤, 새 DB를 사용한 FE·BE·MySQL·Redis·Kafka 배포와 HTTPS 구성을 진행했다. 현재 앱 배포 상태와 운영 절차는 [Azure VM 시연 배포 기록](azure-vm-deployment.md)을 따른다.

## 1. 생성된 구성

모든 리소스는 `${AZURE_RESOURCE_GROUP}`, `koreacentral`에 있다. 구독은 기존 Azure for Students이며 ID는 `${AZURE_SUBSCRIPTION_ID}`이다.

| 대상 | 구성 / 운영 변수 |
| --- | --- |
| VM | `${VM_NAME}` |
| 크기 | `Standard_D2as_v4`, 2 vCPU / 8 GiB, x64 |
| OS | Ubuntu 22.04.5 LTS |
| 이미지 | `Canonical:0001-com-ubuntu-server-jammy:22_04-lts-gen2:22.04.202608060` |
| 보안 유형 | Standard |
| 영구 OS 디스크 | `${OS_DISK_NAME}`, 64 GiB, `StandardSSD_LRS`, 삭제 옵션 `Detach` |
| VNet / 서브넷 | `${VNET_NAME}` / `${SUBNET_NAME}` |
| 주소 범위 | VNet `${VNET_CIDR}`, 서브넷 `${SUBNET_CIDR}` |
| NIC | `${NIC_NAME}`, 현재 사설 IP `${VM_PRIVATE_IP}` |
| NSG | `${NSG_NAME}`, NIC에 연결 |
| 공인 IP | `${PUBLIC_IP_NAME}`, Standard / Regional / Static IPv4 |
| 현재 공인 주소 | `${VM_PUBLIC_IP}` |
| Azure DNS | `danzzan.koreacentral.cloudapp.azure.com` |
| 관리 ID | SystemAssigned |
| 관리 ID principal ID | `${VM_PRINCIPAL_ID}` |
| SSH 사용자 | `danzzan`, 공개 키 인증 |

`Microsoft.Compute`와 `Microsoft.Network` 공급자가 Registered 상태임을 확인했다. 생성 직전 지역 전체 vCPU 쿼터는 6, DASv4 제품군은 4였다. D2as_v5/D2s_v5 제품군은 쿼터가 0이어서 D2as_v4로 변경하고 승인받았다. 동료 구독에서는 공급자 등록·쿼터·SKU 가용성을 다시 확인한다.

2026-10-14 DNS label을 `danzzan-demo-2026`에서 `danzzan`으로 줄였다. 공인 IP는 유지했으며 HTTPS·CORS도 함께 변경했다. 검증과 이전 설정 보관 위치는 [배포 기록](azure-vm-deployment.md)의 DNS 이름 변경 절에 있다.

공인 IP 리소스를 보존하면 VM 할당 취소/시작 시에도 같은 IP와 DNS 이름을 사용할 수 있다. IP 리소스를 삭제하거나 DNS 설정을 변경하면 주소 유지가 보장되지 않는다. 이 DNS 이름은 웹 서버나 TLS 인증서가 자동으로 설치됐다는 의미가 아니다.

## 2. 네트워크와 SSH

추가한 인바운드 규칙:

| 규칙 | 우선순위 | 출발지 | TCP 목적지 |
| --- | ---: | --- | --- |
| `Allow-Web` | 100 | Internet | 80, 443 |
| `Allow-SSH-Operator` | 110 | 생성 시 작업자 공인 IPv4 한 개(`/32`) | 22 |
| `Allow-SSH-Teammate` | 120 | `${TEAMMATE_PUBLIC_IP}/32` (2026-10-15 승인·추가) | 22 |

2026-10-15 동료 DB 접속을 위해 SSH 규칙 한 개만 추가했다. 기존 웹 규칙과 작업자 SSH 출발지 `${OPERATOR_PUBLIC_IP}/32`를 유지했고, NIC의 실제 적용 규칙에서 추가를 확인했다. DB 포트는 공개하지 않았다. 동료의 접속 절차와 검증 범위는 [VS Code DB 연결 안내](azure-db-vscode-connection.md)를 따른다.

DB 3306, Redis 6379, Kafka 9092/9094, Spring 8080을 인터넷에 허용하는 규칙은 만들지 않았다. 이후 Compose에서도 내부 서비스의 호스트 포트를 공개하지 않도록 별도로 구성해야 한다.

전용 RSA 4096비트 키를 현재 작업자의 Mac에 새로 만들었다. 개인 키 권한은 `600`이며 기존 키는 덮어쓰지 않았다. 개인 키는 Git·문서·VM 이미지에 포함하지 않는다. 동료는 자신의 공개 키를 등록해 접속한다.

현재 작업자의 접속 명령:

```bash
: "${SSH_KEY_PATH:?Set SSH_KEY_PATH from the private operations guide}"
ssh -i "${SSH_KEY_PATH}" \
  danzzan@danzzan.koreacentral.cloudapp.azure.com
```

접속 네트워크의 공인 IP가 바뀌면 SSH가 막힐 수 있다. 이때 NSG의 작업자 `/32` 주소를 확인하고 승인된 새 주소로 변경한다. SSH를 `0.0.0.0/0`으로 열어 해결하지 않는다.

VM에는 64 GiB OS 디스크 외에 **16 GiB 임시 디스크 `/mnt`**도 보였다. MySQL·Redis·Kafka 영속 데이터는 `/mnt`에 두지 않는다. Docker 볼륨은 영구 OS 디스크 `/dev/sda1`에 두었고, 서비스 재시작 후 보존을 확인했다. 이 절의 10월 14일 점검 당시에는 Azure 할당 취소·재시작 후 보존을 확인하지 않았다. 이후 [10월 22일 재시작 검증](azure-vm-stop-start.md)에서 보존을 확인했다.

## 3. VM 관리 ID 권한

기존 저장소 `danzzandemo2026` 및 비공개 컨테이너 `images`를 그대로 사용한다. 기존 사용자 역할은 변경하지 않았다.

| 역할 | 할당 범위 | 목적 |
| --- | --- | --- |
| Storage Blob Data Contributor | `images` 컨테이너만 | 이미지 읽기·업로드·삭제 |
| Storage Blob Delegator | `danzzandemo2026` 계정 | 사용자 위임 키를 받아 짧은 읽기 SAS 발급 |

컨테이너 범위는 다음과 같다. Delegator는 이 경로의 `/blobServices/default/containers/images`를 제외한 저장소 계정 범위다.

```text
/subscriptions/${AZURE_SUBSCRIPTION_ID}/resourceGroups/${AZURE_RESOURCE_GROUP}/providers/Microsoft.Storage/storageAccounts/danzzandemo2026/blobServices/default/containers/images
```

향후 BE 실행 설정:

```dotenv
SPRING_PROFILES_ACTIVE=prod,azure
IMAGE_STORAGE_PROVIDER=azure
AZURE_STORAGE_ENDPOINT=https://danzzandemo2026.blob.core.windows.net
AZURE_STORAGE_CONTAINER=images
AZURE_STORAGE_CREDENTIAL=managed-identity
AZURE_STORAGE_READ_SAS_EXPIRATION_SECONDS=300
```

배포된 BE는 위 Azure 설정을 `prod,azure` 프로필과 함께 사용한다. DB·Redis·Kafka·JWT 등 앱 필수 설정은 VM 비밀 파일로 주입하며, 관리 ID 인증에는 저장소 키·연결 문자열을 넣지 않는다.

## 4. 실제 검증과 미검증 범위

실행하여 성공한 항목:

- VM 프로비저닝 `Succeeded`, 전원 `VM running` 확인.
- DNS 이름으로 SSH 접속 성공. OS `x86_64`, Ubuntu 22.04.5, CPU 2개, 메모리 약 7,937 MiB 확인.
- 루트 파일시스템 약 62 GiB, 영구 OS 디스크 64 GiB 및 별도 임시 디스크 위치 확인.
- NIC에 NSG와 정적 공인 IP가 연결됐고, 위 인바운드 규칙만 추가됐음을 확인.
- VM 내부 IMDS에서 시스템 관리 ID의 Storage 토큰 획득 성공.
- 그 토큰으로 비공개 `images`의 Blob 목록 조회: **HTTP 200**.
- 그 토큰으로 5분 유효 사용자 위임 키 발급: **HTTP 200**. 토큰·키 값과 Blob 이름은 출력하지 않음.

이번 검증에서는 **게시물·이미지를 생성·수정·삭제하지 않았다.** Blob 목록 조회와 인증용 위임 키 발급만 수행했다. 계정 키와 익명 공개 접근도 활성화하지 않았다.

이후 실제 앱 배포에서 Docker 내부 Azure SDK 인증, 새 `danzzan_demo`의 `ddl-auto: validate`, FE/BE 실행, HTTPS, 합성 공지를 통한 이미지 업로드·읽기 SAS 갱신과 만료 후 재발급을 확인했다. 임시 공지 ID 2와 테스트 Blob은 정리했고, MySQL·Redis·Kafka 서비스 재시작 뒤 보존도 확인했다. 10월 14일 당시 Azure 할당 취소·재시작은 미검증이었으며, 이후 결과는 [10월 22일 검증 기록](azure-vm-stop-start.md)을 따른다. 상세 범위는 [Azure VM 시연 배포 기록](azure-vm-deployment.md)에 분리한다. 이전 로컬 이미지 연동 검증과 VM 배포 검증은 구분한다.

## 5. 비용과 운영

2026-10-14에 확인한 Korea Central Linux 일반 종량제 기준의 예상치:

- VM: 시간당 $0.118 → 168시간 **$19.82**.
- E6 Standard SSD 64 GiB: 월 $4.80 → 168시간 약 **$1.10**, I/O 별도.
- Standard 공인 IPv4: 시간당 $0.005 → 168시간 **$0.84**.
- 일주일 기본 합계 약 **$21.77**. Blob·디스크 I/O·송신·사전 준비 가동 시간 등은 별도이며, $25~30은 계획 예산이지 상한이 아니다.
- 할당 취소 후에도 디스크와 IP를 유지하면 월 약 **$8.45 + Blob** 비용이 남는다.

VM은 10월 14일 기록 당시 시연 배포·최종 검증을 위해 실행 중이었다. 10월 22일 최종 확인은 할당 취소 상태이며 [중지·재시작 안내](azure-vm-stop-start.md)를 따른다. 자동 종료/할당 취소 일정은 만들지 않았다. 평가 외 기간에는 아래와 같이 포털 또는 CLI에서 실제로 할당 취소해야 컴퓨팅 과금이 멈춘다.

```bash
: "${AZURE_RESOURCE_GROUP:?Set AZURE_RESOURCE_GROUP from the private operations guide}"
: "${AZURE_SUBSCRIPTION_ID:?Set AZURE_SUBSCRIPTION_ID from the private operations guide}"
: "${VM_NAME:?Set VM_NAME from the private operations guide}"
# 컴퓨팅 할당 취소. 디스크·IP·Blob은 보존된다.
az vm deallocate --subscription "${AZURE_SUBSCRIPTION_ID}" \
  --resource-group "${AZURE_RESOURCE_GROUP}" --name "${VM_NAME}"

# 평가 준비 시 시작
az vm start --subscription "${AZURE_SUBSCRIPTION_ID}" \
  --resource-group "${AZURE_RESOURCE_GROUP}" --name "${VM_NAME}"
```

10월 22일에 Azure 할당 취소·재시작 후 서비스와 영구 볼륨 보존을 확인했다. 평가 전에는 해당 안내에 따라 다시 기동하고 기능을 점검한다. 기존 DB 덤프 전체나 실제 게시물·이미지를 자동 이관하지 않는다. `ddl-auto: validate`는 빈 DB에 테이블을 생성하지 않으며, 기존 데이터를 삭제하는 로컬 seed SQL을 그대로 사용하면 안 된다.

참고: [공인 IP](https://learn.microsoft.com/en-us/azure/virtual-network/ip-services/public-ip-addresses), [VM 상태별 과금](https://learn.microsoft.com/en-us/azure/virtual-machines/states-billing), [DNS 이름 가용성 API](https://learn.microsoft.com/en-us/rest/api/virtualnetwork/check-dns-name-availability/check-dns-name-availability?view=rest-virtualnetwork-2025-05-01).
