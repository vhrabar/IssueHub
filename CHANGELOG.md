<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# IssueHub Changelog

## [Unreleased]

## [0.1.0] - 2026-09-02

### Added

- **Settings | Tools | IssueHub** accounts page, with one section per provider listing the accounts stored for it, an **Add Account…** dialog and a **Remove** action. Accounts are application-level, so they are shared by every project.
- Several accounts at once, each keyed to its own server and login, so a work GitHub Enterprise host can sit next to github.com.
- **Use an Account from the IDE…**, which offers the GitHub accounts already signed in under *Settings | Version Control | GitHub* instead of requiring a special token. The IDE's GitHub plugin is an optional dependency, so IssueHub can work without it.
- Tokens are checked against the server when added: the page shows who the token belongs to, the scopes it carries, and warns when `read:project` is missing rather than rejecting the account. Fine-grained tokens, whose scopes GitHub doesn't publish, are reported as such rather than as having none.
- **Create a token on the server…** link that opens GitHub's token page pre-filled with the scopes IssueHub asks for, plus a hint on what a token needs to carry.
- Sidebar down the right of an issue, in the order GitHub's own page uses: assignees, labels, projects, milestone and development. It shows what the list row already knows while the rest is fetched, and sits on a splitter whose position the IDE remembers.
- The **Projects** section lists each board the issue sits on together with the fields that board keeps for it — status, size, estimate, start and target dates, iteration — whatever the board's owner named them.
- The **Development** section lists the pull requests and branches opened for the issue, distinguishing draft, open, merged and closed.
- GitHub GraphQL support behind the two sections REST can't serve, including Enterprise hosts (`HOST/api/graphql` alongside the REST root at `HOST/api/v3`).

### Changed

- The tool window's **Add Token…** placeholder is gone; a **Settings…** button opens the accounts page and the list reloads on the way back.
- Tokens are stored per account rather than per provider. A token saved by an earlier version is adopted into an account on first use.
- An issue carries all of its assignees instead of just one; single-line views still show the first.
- Labels, assignee and milestone moved out of the issue header's chip row and into the sidebar.
- A section the provider couldn't read says so, instead of being shown as empty, a token without `read:project` scope is warned about.

### Compatibility

- Verified against IntelliJ Platform 2025.3 through 2026.2rc
- No internal, deprecated or scheduled-for-removal platform API is used any longer.

## [0.1.0] - 2026-09-02 [YANKED]

- Withdrawn. The upload was rejected by the JetBrains Marketplace over the plugin's use of internal and deprecated platform API.


## [0.0.4] - 2026-08-04

### Added

- Issue rows now show the author, creation date, labels and open/closed state instead of just the title.
- Real user avatars for issue authors and assignees, downloaded in the background with generated initials shown until the picture arrives.
- Label tooltips with colour swatches matching the label colours configured on GitHub.
- Dedicated IssueHub tool window icons for the light and dark themes.
- Search field above the issue list that matches issue titles and bodies as you type.
- Filter dropdowns for state, author, assignee (including *Unassigned*), labels and milestone (including *No milestone*), plus a **Reset** action.
- Sorting by creation date, last update or comment count, ascending or descending.
- Issue detail view opened in the editor area as its own tab, with the title, metadata and the rendered description, plus **Refresh** and **Open on GitHub** actions. Being an editor, it takes the full window width and can be split next to your code.
- The issue tab now shows the full history below the description: comments (rendered, and marked when edited) alongside closes, reopens, label, assignee and milestone changes, title edits, and references from other issues and commits.
- The history reads as a thread of cards, one per run of activity by the same account, with that account's avatar and name down the left and what they did to the right of it. Every entry carries an icon for its kind, and label changes show the label's own colour.

### Changed

- Double-clicking an issue now opens it in the editor instead of the browser; **Open on GitHub** in the issue tab still takes you to GitHub.
- The tool window header shows the **IssueHub** title instead of the generic tool window ID label.
- Rows ellipsize to the panel width, so the horizontal scrollbar is gone.
- Pull requests are filtered out of the issue list; only real issues are shown.

### Known limitations

- Token entry is still a temporary placeholder; there is no dedicated settings/configuration UI yet.
- GitHub is the only supported provider.
- The list shows at most 50 issues per query; there is no pagination.
- An issue's history is read up to 500 entries; anything past that is not shown.
- The author and assignee dropdowns offer repository collaborators plus anyone seen on the loaded issues; the full list needs a token with push access.

### Compatibility

- Verified against IntelliJ Platform 2025.3 through 2026.3rc

## [0.0.3] - 2026-07-24

### Added

- Issue rows now show the author, creation date, labels and open/closed state instead of just the title.
- Real user avatars for issue authors and assignees, downloaded in the background with generated initials shown until the picture arrives.
- Label tooltips with colour swatches matching the label colours configured on GitHub.
- Dedicated IssueHub tool window icons for the light and dark themes.

### Changed

- Double-clicking an issue now opens it in the editor instead of the browser; **Open on GitHub** in the issue tab still takes you to GitHub.
- The tool window header shows the **IssueHub** title instead of the generic tool window ID label.
- Rows ellipsize to the panel width, so the horizontal scrollbar is gone.
- Pull requests are filtered out of the issue list; only real issues are shown.

### Known limitations### Added

- Issue rows now show the author, creation date, labels and open/closed state instead of just the title.
- Real user avatars for issue authors and assignees, downloaded in the background with generated initials shown until the picture arrives.
- Label tooltips with colour swatches matching the label colours configured on GitHub.
- Dedicated IssueHub tool window icons for the light and dark themes.
- Search field above the issue list that matches issue titles and bodies as you type.
- Filter dropdowns for state, author, assignee (including *Unassigned*), labels and milestone (including *No milestone*), plus a **Reset** action.
- Sorting by creation date, last update or comment count, ascending or descending.
- Issue detail view opened in the editor area as its own tab, with the title, metadata and the rendered description, plus **Refresh** and **Open on GitHub** actions. Being an editor, it takes the full window width and can be split next to your code.
- The issue tab now shows the full history below the description: comments (rendered, and marked when edited) alongside closes, reopens, label, assignee and milestone changes, title edits, and references from other issues and commits.
- The history reads as a thread of cards, one per run of activity by the same account, with that account's avatar and name down the left and what they did to the right of it. Every entry carries an icon for its kind, and label changes show the label's own colour.

### Known limitations

- Token entry is still a temporary placeholder; there is no dedicated settings/configuration UI yet.
- GitHub is the only supported provider.
- The list shows at most 50 issues per query; there is no pagination.
- An issue's history is read up to 500 entries; anything past that is not shown.
- The author and assignee dropdowns offer repository collaborators plus anyone seen on the loaded issues; the full list needs a token with push access.

### Compatibility

- Verified against IntelliJ Platform 2025.2 through 2026.2

## [0.0.2] - 2026-07-21

### Added

- **IssueHub** tool window that lists issues for the current repository, with a **Refresh** action and double-click to open an issue in the browser.
- Automatic GitHub repository detection from the project's `.git/config`.
- **Add Token…** action that stores a GitHub personal access token in the IDE's secure credential store (`PasswordSafe`), read-only scope is enough.
- Issue rows showing basic info
- Pluggable issue-provider extension point (`com.github.vhrabar.issuehub.issueProvider`) with a GitHub implementation.

### Known limitations

- Token entry is a temporary placeholder; there is no dedicated settings/configuration UI yet.
- GitHub is the only supported provider.
- The list shows the 50 most recent open issues; no search or filtering.

### Compatibility

- Verified against IntelliJ Platform 2025.2 through 2026.2

[Unreleased]: https://github.com/vhrabar/IssueHub/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/vhrabar/IssueHub/compare/v0.0.4...v0.1.0
[0.0.4]: https://github.com/vhrabar/IssueHub/compare/v0.0.3...v0.0.4
[0.0.3]: https://github.com/vhrabar/IssueHub/compare/v0.0.2...v0.0.3
[0.0.2]: https://github.com/vhrabar/IssueHub/commits/v0.0.2
