<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# IssueHub Changelog

## [Unreleased]

## [0.0.2] - 2026-07-21

### ### Added

- **IssueHub** tool window that lists issues for the current repository, with a **Refresh** action and double-click to open an issue in the browser.                                                                                                                                                                                                                            
- Automatic GitHub repository detection from the project's `.git/config`.                                                                                                                                                                                                                                                                                                       
- **Add Token…** action that stores a GitHub personal access token in the IDE's secure credential store (`PasswordSafe`), read-only scope is enough.                                                                                                                                                                                                                           
- Issue rows showing basic info                                                                                                                                                                                                                                                                                
- Pluggable issue-provider extension point (`com.github.vhrabar.issuehub.issueProvider`) with a GitHub implementation.                                                                                                                                                                                                                                                          

### ### Known limitations

- Token entry is a temporary placeholder; there is no dedicated settings/configuration UI yet.                                                                                                                                                                                                                                                                                  
- GitHub is the only supported provider.                                                                                                                                                                                                                                                                                                                                        
- The list shows the 50 most recent open issues; no search or filtering.                                                                                                                                                                                                                                                                                                        

### ### Compatibility

- Verified against IntelliJ Platform 2025.2 through 2026.2

[Unreleased]: https://github.com/vhrabar/IssueHub/compare/v0.0.2...HEAD
[0.0.2]: https://github.com/vhrabar/IssueHub/commits/v0.0.2
