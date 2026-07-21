<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# IssueHub Changelog

## [0.0.1] - 2026-07-21
### Added
- Initial alpha release.
- **IssueHub** tool window listing GitHub issues for the current repository, with refresh and
  double-click-to-open-in-browser.
- Automatic GitHub repository detection from the project's `.git/config`.
- Secure per-provider token storage backed by the IDE's `PasswordSafe` credential store.
- **Add Token…** action to store a GitHub personal access token (read-only scope; see README).
- Pluggable issue-provider extension point  with a
  GitHub implementation.
- Verified compatible with IntelliJ Platform 2025.2 through 2026.2.

###  Limitations
- Token entry is a temporary placeholder; no dedicated settings/configuration UI yet.
- GitHub is the only supported provider.
- Issue list is limited to the 50 most recent open issues; no search or filtering.

[Unreleased]: https://github.com/vhrabar/IssueHub/compare/v0.0.1...HEAD
[0.0.1]: https://github.com/vhrabar/IssueHub/releases/tag/v0.0.1
