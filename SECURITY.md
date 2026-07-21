## Security

The only sensitive data IssueHub handles is your issue-tracker access token.

- **Where it's stored.** Tokens are never written to plugin settings, project files, or logs. They are kept
  in the IDE's secure credential store via the platform [`PasswordSafe`][docs:sensitive-data] API, which
  delegates to the OS-native keychain where available and falls back to an encrypted store otherwise.
- **How it's transmitted.** The token is sent only to the tracker's official API over HTTPS, in the standard
  `Authorization` header. IssueHub contacts no other hosts and collects no telemetry.

[docs:sensitive-data]: https://plugins.jetbrains.com/docs/intellij/persisting-sensitive-data.html
