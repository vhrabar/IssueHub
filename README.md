<!-- Light mode -->
![Logo (light)](assets/issuehub-banner-light.svg#gh-light-mode-only)

<!-- Dark mode -->
![Logo (dark)](assets/issuehub-banner-dark.svg#gh-dark-mode-only)
# IssueHub

![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/vhrabar/IssueHub/build.yml?style=for-the-badge)
[![Version](https://img.shields.io/jetbrains/plugin/v/33044.svg?style=for-the-badge)](https://plugins.jetbrains.com/plugin/33044)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/33044.svg?style=for-the-badge)](https://plugins.jetbrains.com/plugin/33044)


<!-- Plugin description -->
**IssueHub** brings your GitHub issues into the IDE. Browse and open issues for the current
repository from a dedicated tool window, without leaving your editor.

- Lists issues for the GitHub repository detected from your project's Git remote
- Shows issue number, title, labels, and assignee
- Search issue titles and bodies, and filter by state, author, assignee, labels or milestone
- Sort by creation date, last update or comment count
- Opens an issue as an editor tab, full width and splittable next to your code, with the description
  rendered in the IDE's own styling
- Reads the whole thread in that tab: comments alongside closes, reopens, label, assignee and
  milestone changes, title edits, and references from other issues and commits
- Keeps the issue's assignees, labels, projects (with the fields each board tracks, such as status,
  size, estimate and dates), milestone, and the pull requests and branches opened for it in a
  sidebar beside the thread, the way GitHub's own issue page does
- Jumps to the issue on GitHub whenever you need the browser
- Keeps accounts under **Settings | Tools | IssueHub**: paste a token and have it checked against the
  server before it is saved, or adopt a GitHub account your IDE is already signed in with, on
  github.com or on an Enterprise host
- Stores every token in the IDE's secure credential store, never in plain text

<!-- Plugin description end -->

## Usage (Alpha)

1. Open a project whose Git remote points at a GitHub repository, IssueHub reads the repo from
   `.git/config` automatically.
2. Open the **IssueHub** tool window.
3. Click **Settings…** to open **Settings | Tools | IssueHub**, then add an account: paste a personal
   access token, or pick one of the GitHub accounts the IDE is already signed in with under
   *Settings | Version Control | GitHub*. The token is checked before it is saved, and the page then
   shows whose account it is and which scopes it carries. Public repositories are readable without an
   account, at a much lower rate limit.
4. Click **Refresh** to load issues. Double-click an issue to open it as an editor tab, titled with
   the issue number; **Open on GitHub** there opens the same issue in your browser.
5. The issue tab shows the description and, below it, the issue's history as a thread of cards:
   comments plus the closes, reopens, label, assignee and milestone changes and title edits around
   them. **Refresh** in that tab re-reads the issue from GitHub.
6. The sidebar down the right of that tab carries **Assignees**, **Labels**, **Projects**,
   **Milestone** and **Development**, the way GitHub's own issue page does. **Projects** lists each
   board the issue is on together with that board's own fields for it, whatever they are called:
   status, size, estimate, start and target dates, iteration. **Development** lists the pull requests
   that will close the issue, marked open, draft, merged or closed, and the branches opened for it.
   Drag the divider to resize the sidebar; the IDE remembers where you left it.
7. Use the search field and the **State / Author / Assignee / Label / Milestone / Sort** dropdowns to
   narrow the list; **Reset** clears everything back to open issues, newest first.

Filtering and sorting run on GitHub's side, so the results are the whole repository's issues, not
just the ones already on screen. Typing in the search field uses GitHub's search endpoint, which is
rate limited more tightly than the plain issue list, an authenticated token raises that limit
considerably.

### Required token scope

| Repository | Token type | Scope / permission needed                                                                                                                                |
|------------|------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Public** | none, or any token | Reads without authentication (60 req/hr); any token, even one with **no scopes**, just raises the rate limit.                                            |
| **Private** | fine-grained PAT | Repository access + **Issues: Read-only** (Metadata: Read is included automatically).                                                                    |
| **Private** | classic PAT | `repo`: note this is the *only* classic scope that reads private repos, and it grants full read/write. Prefer a fine-grained token for read-only access. |
| **Any**, for the sidebar's **Projects** and **Development** | fine-grained or classic | Both read GitHub's GraphQL API, which needs a token even on a public repo. Boards additionally need **Projects: Read-only** (fine-grained) or `read:project` (classic). |

IssueHub only reads issues (for now), so it never needs write access. For private repos, prefer a
fine-grained token with **Issues: Read-only** as classic tokens can't scope down to read-only. Once an
account is saved, the accounts page lists the scopes its token actually carries and warns when
`read:project` is missing; GitHub publishes no scopes for fine-grained tokens, so for those it says so
instead of guessing.

## Installation

[![Install from JetBrains Marketplace](https://img.shields.io/badge/JetBrains%20Marketplace-Install-blue?logo=jetbrains&style=for-the-badge)](https://plugins.jetbrains.com/plugin/33044)

- Using the IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "IssueHub"</kbd> >
  <kbd>Install</kbd>

- Using JetBrains Marketplace:

  Go to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/33044) and install it by clicking the <kbd>Install to ...</kbd> button in case your IDE is running.

  You can also download the [latest release](https://plugins.jetbrains.com/plugin/33044/versions) from JetBrains Marketplace and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

- Manually:

  Download the [latest release](https://github.com/vhrabar/IssueHub/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

## License
This project is licensed under the Apache License v2 - see the [LICENSE](LICENSE) file for details

Copyright © 2026 Vedran Hrabar

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
