## Security

The only sensitive data IssueHub handles is your issue-tracker access token.

- **Where it's stored.** Tokens are never written to plugin settings, project files, or logs. They are kept
  in the IDE's secure credential store via the platform [`PasswordSafe`][docs:sensitive-data] API, which
  delegates to the OS-native keychain where available and falls back to an encrypted store otherwise.
- **What is kept alongside it.** An account's provider, server URL and login are ordinary settings and
  live in `issuehub.xml`; the token never does. Each token is filed in the credential store under the
  account's own id, so several accounts, on github.com and on an Enterprise host, can be held at once.
- **Accounts adopted from the IDE.** *Use an Account from the IDE…* reads the credential the IDE's own
  GitHub plugin already holds for an account you are signed in with, and stores a copy under IssueHub's
  own entry in the same credential store. That plugin is read only while the dialog is open.
- **How it's transmitted.** The token is sent only to the tracker's official API over HTTPS, in the standard
  `Authorization` header. IssueHub contacts no other hosts and collects no telemetry.

[docs:sensitive-data]: https://plugins.jetbrains.com/docs/intellij/persisting-sensitive-data.html
