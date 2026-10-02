# Ticket settings implementation plan

Goal: Share editable ticket rounds, OFF artwork, and dates/capacity between festival settings, ticket settings, booking, and wristband administration.
Architecture: Reuse the festival singleton and linked festival_events. Extend the ticketing-only API and existing admin editor; do not create a second round store.
Tech stack: Spring Boot/JPA/MySQL, React/TypeScript.
Spec: User-approved conversation requirements (DANZ-368 FE / DANZ-369 BE).

## Constraints
- Latest main in isolated clones, local DB only. Preserve accounts and existing tickets.
- Login/signup/profile stay available with ticketing OFF; existing-ticket viewing and wristband operations remain available.
- Performance date belongs to festival dates; opened/issued rounds cannot be edited.
- Uploaded artwork is staged, applied on Save, cleared with explicit null; omission preserves prior artwork.

## Tasks
- [x] BE: tests for OFF preservation, round synchronization, locked unchanged rounds, invalid IDs, and image validation; implement optional image field, multipart upload and nullable migration. Add opening time to admin event DTO.
- [x] FE: shared editor at /admin/ticketing with edit/save/cancel, round inputs, image upload/preview/reset; navigation with wristband arrow. Update public settings store on save/focus and show custom OFF background.
- [x] Display persisted event date/opening time/capacity on booking and wristband cards; remove date remapping. Test permissions, round identity, cancel, OFF routes, mappings.
- [x] Run relevant BE tests and FE tests/typechecks/structure/build/budget. Rebuild local Docker app/FE preserving image mount and infra. Verify API role matrix and real browser flows; self-review and commit/push feature branches.

## Review focus
Missing old image field must preserve image. Upload errors must keep editable state. Locked rows must survive unrelated updates. Round IDs must not silently create duplicate events. OFF must not erase issued tickets. No credentials committed.

## Validation record
- FE: 111 targeted tests, app/test typechecks, changed-file structure checks, production build and bundle budget passed.
- BE: 28 targeted tests passed; bootJar built. Local four-role API matrix verified.
- Browser: existing-round capacity saved from 35 to 40; cancelled 99 restored 40. User booking and wristband views both showed May 14 / May 2 19:30 / 40 tickets. JPG selected and saved from the actual form; OFF screen image loaded.
- OFF: login/signup/profile accessible, booking GET returns 403, own tickets and wristband GET return 200.
- Local DB backup before-ticket-settings.sql retained outside Git. Disposable future festival/round removed after verification; historical users/events/booths preserved.
- Existing AWS dev image bucket returned NoSuchBucket. Local Docker S3-compatible storage was added for tests, with persistent volume and loopback-only port 9000. Production still needs a valid bucket configuration.
- Self-review (no subagent tool): permission routing for image upload fixed after live 403 reproduction; mobile menu click expansion fixed after failing regression test; event row locked during edits and auto-open to protect capacity initialization. No merge performed.
