# Theme color policy

Newly generated and newly saved themes preserve their four brand colors. The backend then applies the same fixed semantic and map policy to every write:

| Field | Value |
| --- | --- |
| `category.pub` | `brand.primary` |
| `status.success` | `#2E7D32` |
| `status.warning` | `#B26A00` |
| `status.danger` | `#B3261E` |
| `status.pending` | `#E3776A` |
| `category.foodTruck` | `#EC4899` |
| `category.experience` | `#10B981` |
| `category.event` | `#F6CA3B` |
| `category.restArea` | `#8B5CF6` |
| `category.facility` | `#3B82F6` |

The policy applies to AI recommendation results and to create, update, and apply-bundle writes. Reading or activating an existing saved theme does not rewrite it, so production data changes only after an explicit new save.

## Verification

`./gradlew test --tests 'com.danzzan.domain.festival.theme.service.ThemeColorPolicyTest' --tests 'com.danzzan.domain.festival.theme.ThemeServiceTest' --tests 'com.danzzan.domain.festival.theme.ThemeRecommendationTest'` passed 40 tests. The check uses mocked storage and the OpenAI transport test server only; it does not call OpenAI, deploy, or change database records.

## Azure deployment (2026-10-14)

Release `20261014-theme-policy` deployed the backend and frontend together. Both VM Docker builds succeeded; the running app is healthy and HTTPS checks passed. Browser verification used an unsaved new-theme draft to check fixed colors and Primary/pub synchronization, then discarded it. No live OpenAI request or theme save/activation was performed. The content hash for 20 existing tables matched before deployment and after browser verification. See `azure-vm-deployment.md` for recovery artifacts.
