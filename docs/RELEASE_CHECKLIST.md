# Release and update checklist

This is a personal sideloadable debug build, not a public distribution claim. Check [implementation status](../IMPLEMENTATION_STATUS.md) for actual results rather than treating this checklist as passed.

## Signing continuity

The established CI debug certificate SHA-256 is:

`fe2db971ead2186305b3fcac03c4b72aee0e535e310a5cebed82d2be578f646b`

CI restores the existing `bare-bones-android-debug-signing-v1` cache and verifies the certificate before building. A missing/different key fails the build; CI never silently generates an incompatible replacement. Native tests use the same identity. No private key is committed or uploaded in artifacts.

For durable recovery, the repository owner should retain a **private backup of that existing key**, and configure repository Actions secret `BBD_DEBUG_KEYSTORE_BASE64` containing its base64 bytes. The optional secret takes precedence over the cache; its certificate must match the pinned public fingerprint. The standard debug alias/password are `androiddebugkey` / `android`. Do not put the key, its base64 text or a release password in an issue/chat/commit. No durable secret backup is asserted configured by this task; cache eviction therefore still requires restoration of the existing key before another CI APK can be published.

Local SDK debug keys generally differ from CI. To update an installed CI APK locally, use the existing privately backed-up CI key. Otherwise Android correctly rejects `adb install -r`. Do not recommend uninstalling as a data-preserving update.

## Automated gates

- Unit/Robolectric tests; APK/instrumentation compilation; lint with no errors.
- Real Android native codec/container, selected/deselected tags, ReplayGain and unchanged-audio checks.
- Strict audio/video cap under/over/impossible boundaries; final tag overhead included; seven-attempt bound.
- Real DocumentsUI grants, collision protection, read-back hash, cancellation and publication-journal recovery.
- Owned HTTP source Audio A / Video A / Video B FIFO while screen is off; no automatic work from a share.
- Light/dark at increased font scale, with screenshots inspected.
- In-place `adb install -r` preserves preferences, waiting reservations and history. `tools/check-update.py` seeds these in the old APK and verifies them in the new one. The baseline artifact run is pinned in the workflow; replace it with a retained same-certificate baseline when it expires. If unavailable the update test explicitly reports SKIPPED, not passed.
- Record live YouTube and TikTok results separately. Site authentication, removal, geography or extractor changes are limitations; do not label them as passing downloads. Do not add cookie/login or bypass workarounds.

## Device / owner checks still required where untested

- A physical Android device and the intended ARM ABI; current Android service limits, permission prompts and OEM battery policies; 16 KB page-size compatibility on affected devices.
- TalkBack traversal/status announcements, 200% text, landscape, keyboard navigation and launcher icon rendering.
- Provider-specific behavior (cloud/offline/removable storage, revoked permissions, manual cleanup when delete is denied). SAF cannot guarantee atomic publication across every provider. A hard process kill between provider creation and journal commit is a small unavoidable cross-store transaction gap; inspect the folder if interrupted at that point.
- Accessible user-authorized live examples for each intended website; authoritative metadata and catalogue matches. Generated fixtures verify processing, not live site access or every music credit.
- Durable private signing backup, current GPL notices, complete corresponding-source/build bundle and compatible licensing before wider redistribution.
