#!/usr/bin/env bash
# Run once by the project owner in Google Cloud Shell after reviewing the exact access grant.
# No service-account private key is created. No billing account is attached.
set -euo pipefail
project=hamigo-play-ci
number=989867563674
account=hamigo-play-ci@hamigo-play-ci.iam.gserviceaccount.com
condition="assertion.repository_id == '1403424361' && assertion.repository_owner_id == '54336210' && ((assertion.ref.startsWith('refs/tags/v') && assertion.workflow_ref.startsWith('AlexMalfr/Hamigo/.github/workflows/play.yml@refs/tags/v')) || (assertion.ref == 'refs/heads/main' && assertion.workflow_ref == 'AlexMalfr/Hamigo/.github/workflows/play-preflight.yml@refs/heads/main'))"
gcloud services enable androidpublisher.googleapis.com iam.googleapis.com iamcredentials.googleapis.com sts.googleapis.com --project="$project"
if ! gcloud iam service-accounts describe "$account" --project="$project" >/dev/null 2>&1; then
  gcloud iam service-accounts create hamigo-play-ci --display-name='Hamigo Play release CI' --project="$project"
fi
if ! gcloud iam workload-identity-pools describe github-actions --location=global --project="$project" >/dev/null 2>&1; then
  gcloud iam workload-identity-pools create github-actions --location=global --display-name='Hamigo GitHub Actions' --project="$project"
fi
if ! gcloud iam workload-identity-pools providers describe github --workload-identity-pool=github-actions --location=global --project="$project" >/dev/null 2>&1; then
  gcloud iam workload-identity-pools providers create-oidc github --location=global --workload-identity-pool=github-actions --project="$project" \
    --issuer-uri='https://token.actions.githubusercontent.com' \
    --attribute-mapping='google.subject=assertion.sub,attribute.repository_id=assertion.repository_id,attribute.repository_owner_id=assertion.repository_owner_id' \
    --attribute-condition="$condition"
else
  gcloud iam workload-identity-pools providers update-oidc github --location=global --workload-identity-pool=github-actions --project="$project" \
    --attribute-condition="$condition"
fi
gcloud iam service-accounts add-iam-policy-binding "$account" --project="$project" \
  --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/projects/$number/locations/global/workloadIdentityPools/github-actions/attribute.repository_id/1403424361"
gcloud iam workload-identity-pools providers describe github --location=global --workload-identity-pool=github-actions --project="$project" --format='yaml(name,state,attributeMapping,attributeCondition)'
gcloud iam service-accounts get-iam-policy "$account" --project="$project"
printf 'WIF bootstrap complete. Invite this service account to the Hamigo app only in Play Console: %s\n' "$account"
