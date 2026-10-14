# 홈 대표 이미지 저장 API

테마 관리 화면에서 이미 업로드하거나 포스터로부터 만든 홈 배너 초안을 저장할 때 사용한다. 이 API는 홈 이미지 목록의 첫 번째 항목만 바꾸며, 두 번째 이후 이미지와 표시 순서는 유지한다.

## 요청

`PUT /admin/festival/{festivalSettingId}/home-images/first`

운영진 관리 권한(`OPERATIONS`)이 필요하다.

```json
{
  "imageUrl": "azure:images/festival/1/home/...",
  "expectedFirstImageId": 12,
  "expectedFirstImageUrl": "azure:images/festival/1/home/...",
  "sourcePosterUrl": "azure:images/festival/1/poster/..."
}
```

- `imageUrl`은 먼저 `/home-images/staged`로 올린 같은 축제의 홈 이미지여야 한다. 저장 전에 저장소 객체와 경로를 확인한다.
- `expectedFirstImageId`와 `expectedFirstImageUrl`은 미리보기 당시 첫 홈 이미지다. 그 사이 목록이 바뀌면 `409 Conflict`가 반환된다.
- 포스터에서 생성한 초안이면 `sourcePosterUrl`에 미리보기 때 사용한 포스터를 보낸다. 잠금 안에서 현재 포스터와 비교하므로 포스터가 바뀐 초안은 저장되지 않는다. 별도 이미지 등록으로 올린 이미지에는 `null`을 보낸다.

성공하면 현재 첫 홈 이미지의 `id`, `imageUrl`, `displayOrder`를 반환한다. 첫 이미지가 없었다면 표시 순서 `0`으로 새로 만든다.

## 동시성 및 범위

축제 설정 행을 잠가 포스터 확인, 첫 이미지 비교, 교체를 하나의 트랜잭션에서 처리한다. 저장된 파일은 다른 레코드에서 참조될 수 있으므로 이 API는 파일 삭제를 수행하지 않는다.

## 검증 및 배포

2026-10-14 커밋 정리 과정에서 홈 저장 서비스·동시성·렌더러·API 통합 테스트 31개를 다시 통과했다. H2와 테스트 저장소를 사용했으며 운영 데이터는 변경하지 않았다. Azure 반영 기록과 복구 방법은 [배포 문서](azure-vm-deployment.md)의 `20261014-home-theme` 및 `20261014-theme-policy` 항목에 기록했다.
