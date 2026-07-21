# IssueHub

![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/vhrabar/IssueHub/build.yml?style=for-the-badge)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)



>  **Alpha.** Core flow works (detect repo > add token > list issues). Configuration is a
> placeholder, GitHub is the only supported provider.

## Usage (Alpha)

1. Open a project whose Git remote points at a GitHub repository, IssueHub reads the repo from
   `.git/config` automatically.
2. Open the **IssueHub** tool window.
3. Click **Add Token…** and paste a GitHub personal access token (stored in the IDE's secure
   credential store, never in plain text).
4. Click **Refresh** to load issues. Double-click an issue to open it in your browser.

### Required token scope

| Repository | Token type | Scope / permission needed                                                                                                                                |
|------------|------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Public** | none, or any token | Reads without authentication (60 req/hr); any token, even one with **no scopes**, just raises the rate limit.                                            |
| **Private** | fine-grained PAT | Repository access + **Issues: Read-only** (Metadata: Read is included automatically).                                                                    |
| **Private** | classic PAT | `repo`: note this is the *only* classic scope that reads private repos, and it grants full read/write. Prefer a fine-grained token for read-only access. |

IssueHub only reads issues (for now), so it never needs write access. For private repos, prefer a
fine-grained token with **Issues: Read-only** as classic tokens can't scope down to read-only.

## Installation

- Using the IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "IssueHub"</kbd> >
  <kbd>Install</kbd>

- Using JetBrains Marketplace:

  Go to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID) and install it by clicking the <kbd>Install to ...</kbd> button in case your IDE is running.

  You can also download the [latest release](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID/versions) from JetBrains Marketplace and install it manually using
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
