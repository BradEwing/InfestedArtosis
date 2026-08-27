# BASIL continuous delivery

`release.yaml` has two jobs:

1. `release` — bumps `pom.xml`, builds the jar, pushes the release commit and creates the GitHub Release.
2. `publish-basil` — downloads that release's jar, zips it with the repo's `BWAPI.dll`, and replaces
   the content of the Google Drive file the BASIL organizer links to.

The organizer's link points at a single Drive **file** and their side does an exact-name match on
`0.38.zip`, so the job never creates or renames anything: it overwrites the content of that one file
in place via `PATCH /upload/drive/v3/files/{id}?uploadType=media`. The file ID, name and link never
change; only the bytes do.

To re-push an existing release without cutting a new one, run the workflow with `republish_version`
set to an existing tag (e.g. `0.61`).

## Security model

- **No long-lived Google credential exists anywhere.** The job authenticates with GitHub's OIDC
  token via Workload Identity Federation and receives a 5-minute access token for a service account.
- The service account has **no IAM roles** in the GCP project. Its only capability is whatever is
  shared with it in Drive: exactly one file, as Editor. It cannot list, read or write anything else
  in your Drive.
- The Workload Identity Pool only accepts tokens from this repository on `refs/heads/main`.
- The `publish-basil` job runs in the `basil` GitHub environment; add required reviewers there if
  you want a manual approval gate before each upload.
- `google-github-actions/auth` is pinned to a commit SHA. The upload itself is plain `curl`, so no
  third-party action ever holds the token. The token is passed to curl via a header file, not argv.
- After upload the job re-reads the file's `md5Checksum` from Drive and fails if it does not match
  the zip it built.

## One-time setup

All values below are identifiers, not secrets; they go in repository **Variables**
(Settings → Secrets and variables → Actions → Variables).

### 1. GCP project

Create a project (e.g. `infested-artosis-cd`) in the Google account that owns the Drive file, then:

```sh
gcloud config set project infested-artosis-cd
gcloud services enable iamcredentials.googleapis.com sts.googleapis.com drive.googleapis.com
```

### 2. Service account (no roles)

```sh
gcloud iam service-accounts create basil-publisher \
  --display-name="BASIL publisher (GitHub Actions)"
```

Note the email: `basil-publisher@infested-artosis-cd.iam.gserviceaccount.com`. Do **not** grant it
any project roles and do **not** create a key for it.

### 3. Workload Identity Federation

```sh
PROJECT_NUMBER=$(gcloud projects describe infested-artosis-cd --format='value(projectNumber)')

gcloud iam workload-identity-pools create github \
  --location=global --display-name="GitHub Actions"

gcloud iam workload-identity-pools providers create-oidc github-infested-artosis \
  --location=global --workload-identity-pool=github \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.ref=assertion.ref" \
  --attribute-condition="assertion.repository == 'BradEwing/InfestedArtosis' && assertion.ref == 'refs/heads/main'"

gcloud iam service-accounts add-iam-policy-binding \
  basil-publisher@infested-artosis-cd.iam.gserviceaccount.com \
  --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/github/attribute.repository/BradEwing/InfestedArtosis"

gcloud iam workload-identity-pools providers describe github-infested-artosis \
  --location=global --workload-identity-pool=github --format='value(name)'
```

The last command prints the provider resource name
(`projects/<number>/locations/global/workloadIdentityPools/github/providers/github-infested-artosis`).

### 4. Share the Drive file

In Google Drive, open `0.38.zip` → Share → add
`basil-publisher@infested-artosis-cd.iam.gserviceaccount.com` as **Editor**, with notifications off.
Share only this file, not the folder.

### 5. GitHub configuration

Repository variables:

| Variable | Value |
| --- | --- |
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | provider resource name from step 3 |
| `GCP_SERVICE_ACCOUNT` | `basil-publisher@infested-artosis-cd.iam.gserviceaccount.com` |
| `BASIL_DRIVE_FILE_ID` | `1hiwnRJpkiPuKOfNbqyuoM13vtL4AhADD` |

Create an environment named `basil` (Settings → Environments). Optionally add yourself as a required
reviewer to gate uploads.

### 6. Smoke test

Run the Release workflow with `republish_version` set to the current version. The job should log the
Drive file's `md5Checksum` before and after and finish with `Published <version> to BASIL Drive file`.
The link the organizer has will now serve the new zip.

## Rotating / revoking

There is nothing to rotate. To revoke, remove the service account from the Drive file's sharing
list or delete the Workload Identity Pool; either immediately stops the workflow from publishing.
