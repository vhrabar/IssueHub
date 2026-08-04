<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# IssueHub Changelog

## [Unreleased]

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

[Unreleased]: https://github.com/vhrabar/IssueHub/compare/v0.0.4...HEAD
[0.0.4]: https://github.com/vhrabar/IssueHub/compare/v0.0.3...v0.0.4
[0.0.3]: https://github.com/vhrabar/IssueHub/compare/v0.0.2...v0.0.3
[0.0.2]: https://github.com/vhrabar/IssueHub/commits/v0.0.2
