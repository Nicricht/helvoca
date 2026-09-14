# Railway autodeploy smoke test

Purpose: verify that a successful merge to `main` triggers Railway automatically after GitHub Actions completes.

This file has no runtime effect.

Expected sequence:
1. PR CI succeeds.
2. PR is merged to `main`.
3. Push CI for the merge commit succeeds.
4. Railway creates a deployment for the same commit without changing service variables or manually redeploying.
5. Production startup remains fail-closed for Twilio certification because no `TWILIO_CERTIFICATION_RUN_ID` is provided.
