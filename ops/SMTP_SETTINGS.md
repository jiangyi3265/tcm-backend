# SMTP settings

Administrators can configure email delivery under **System Settings → SMTP email delivery**.
`GET/PUT /api/settings/smtp` and `POST /api/settings/smtp/test` require the administrator role.
The connection test authenticates without submitting a message.

For Resend, use host `smtp.resend.com`, port `465`, SSL/TLS, username `resend`,
and a new API key as the password. The sender must use a verified domain.
See https://resend.com/docs/send-with-smtp . Revoked keys cannot be restored.

An empty password preserves the existing credential. A changed host or username
requires a new password. Saving takes effect for subsequent messages without a restart.
Until the first admin save, existing Spring mail configuration remains active.

The dedicated `smtpSettings` database record contains AES-256-GCM ciphertext.
Its encryption key defaults to `${user.home}/.otcm/smtp-settings.key`, outside the
application and upload folders. Override with `tcm.smtp.key-file` when necessary.
The service account must be able to create/read this private directory. On POSIX
the key is created with mode 0600 and its new directory with mode 0700.
Preserve this key across deployments and include it in restricted operational backups,
separately from the database. Never commit it or expose it through file-serving routes.
Loss of the key requires re-entering the SMTP password. No implicit fallback to an old
credential occurs after an administrator has saved SMTP settings.

The settings bundle, API response and audit log omit the password and ciphertext.
Provider error details are not recorded because they may echo credentials.
CI rejects Resend key patterns in tracked files without printing matched values.
This prevents new releases containing a key; it does not remove old Git objects.
