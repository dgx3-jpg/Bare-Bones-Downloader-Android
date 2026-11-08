# Release and update checklist

Private personal debug build. Actual results are in [implementation status](../IMPLEMENTATION_STATUS.md).

## Permanent signing

Replacement certificate SHA-256:

`80bd2bc24c4cf2e4710089a66bbb4eeba6323f840b913349936f729887696091`

The earlier CI keys were not retained because their cache path was incorrect. Their private keys cannot be recovered from APKs. The owner instructed the assistant to fix the problem; a new permanent identity was created with a private backup. Older differently signed APKs require one clean install (app-private settings/history reset). No uninstall or data removal on the owner's device is automated.

The permanent private key is kept outside the repository and is **never stored in Actions caches or public artifacts**. Keep the private backup in two secure locations. It contains the keystore, public certificate and restore/build instructions. Debug convention: alias `androiddebugkey`, keystore/key password `android`; possession of the file grants signing authority, so keep it private.

`tools/build-release.py` requires a clean committed checkout and the private key path. It builds baseline code8 / current code9 / instrumentation APKs with the same key, embeds the source commit, produces hash receipts and verifies the pinned signer. The baseline is explicitly an update fixture built from current source, not an assertion that the lost old certificate was recovered.

CI can verify these signed public binaries from a release using `release_tag`, then publish the installable artifact and run the actual permanent-key upgrade/data-preservation test. Source push/PR jobs use isolated temporary keys and publish reports, not a misleading temporary-key release APK. A repository owner may optionally configure `BBD_DEBUG_KEYSTORE_BASE64` through Actions secrets for direct CI signing. The connected integration lacks secret-administration permission (HTTP403); this is no longer required for offline release delivery.

## Gates

- Host tests, lint and app/test assembly.
- Signed asset hashes, source-commit metadata, package/version and public certificate verification.
- Actual `adb install -r` update preserves preferences, waiting reservations and completed history; old-certificate updates remain impossible without that old key.
- Native codec/decode, strict caps (including tag overhead), tag selection/removal, gain/peak and unchanged encoded samples.
- Real SAF grant/read-back hash, collisions, cancellation and recovery of owned partial documents while protecting committed bytes.
- Owned HTTP Audio A / Video A / Video B FIFO and foreground service while screen is off; shared text only prefills input.
- Scaled light/dark screenshots inspected.
- Live YouTube/TikTok results recorded separately; authentication/IP blocks are site limitations. No cookie/login, proxies, client impersonation or bypass workaround is added.

## Remaining manual limits

Physical phone/ARM/16KB-page/OEM battery checks, TalkBack/200% text/landscape/launcher checks, provider-specific deletion/grant loss and actual live catalogue/source availability remain separate device/release checks where untested. SAF has no universal atomic/exclusive rename; a hard kill between provider creation and journal commit can require manual cleanup. Wider distribution requires complete GPL corresponding-source/build materials and compatible licensing; no project-wide license over the owner's original code is inferred.
