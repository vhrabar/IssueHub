<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# IssueHub Changelog

## [Unreleased]

### Added

- Issue rows now show the author, creation date, labels and open/closed state instead of just the title.
- Real user avatars for issue authors and assignees, downloaded in the background with generated initials shown until the picture arrives.
- Label tooltips with colour swatches matching the label colours configured on GitHub.
- Dedicated IssueHub tool window icons for the light and dark themes.

### Changed

- The tool window header shows the **IssueHub** title instead of the generic tool window ID label.
- Rows ellipsize to the panel width, so the horizontal scrollbar is gone.
- Pull requests are filtered out of the issue list; only real issues are shown.

### Known limitations

- Token entry is still a temporary placeholder; there is no dedicated settings/configuration UI yet.
- GitHub is the only supported provider.
- The list shows the 50 most recent open issues; no search or filtering.

### Compatibility

- Verified against IntelliJ Platform 2025.2 through 2026.2