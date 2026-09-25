# Production deployment

This repository uses `master` as its only working branch. GitHub Actions tests and publishes `master` to the production server `152.136.191.51`, service `yuanyuan-tcm`, under `/opt/yuanyuan-tcm`. It does not publish to the former test server.

Production sites:

- https://yuanyuanyiyuan.oksja.cn
- https://yuanyuanyiyuanht.oksja.cn

The `PRODUCTION_DEPLOY_KEY` repository secret is restricted by the server's `authorized_keys` forced command to the backend operation in `/opt/yuanyuan-tcm/bin/production_deploy.py`. It cannot run an arbitrary shell command. The frontend repository has a separate key restricted to the frontend operation. The server host key is pinned in `.github/production_known_hosts`.

The deployment helper validates the artifact checksum, backs up the running artifact, installs it, and checks application health. Backend health failures restore the previous JAR. A shared server-side lock serializes frontend and backend deployment. Backups are recorded under `/www/backup/yuanyuan-tcm/releases`, and `/opt/yuanyuan-tcm/DEPLOYED` records each component's commit and rollback path.

PDF generation requires `/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc` on Linux. The CI workflow installs `fonts-wqy-zenhei` before running the existing bilingual PDF tests.

Database contents, private files, and `/opt/yuanyuan-tcm/config` are not replaced by CI. Runtime credentials are maintained on the production server and must not be committed. The previous test-server deployment secrets are not used by this workflow.

The server-side helper is administrator-managed: changes to `ops/production_deploy.py` must be tested and installed on the server by an administrator before a workflow depends on new helper behavior. CI keys cannot modify the helper itself.
