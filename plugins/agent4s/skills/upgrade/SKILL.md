---
name: upgrade
description: "Update the installed agent4s Claude Code plugin from its marketplace and verify the engine selected by the updated launcher."
disable-model-invocation: true
---

# Update agent4s

Check the current plugin version with `claude plugin list`, then refresh the marketplace and update the named plugin. If the installation has a project or local scope, run the commands from that project and use the same scope for the update.

```sh
claude plugin marketplace update agent4s-marketplace
claude plugin update agent4s@agent4s-marketplace
```

Report the update command's result and the installed plugin version from `claude plugin list`. If a command fails, report its error before claiming an update.

Reload plugins with `/reload-plugins` or start a fresh Claude Code session. Run `/agent4s:doctor` there to verify the active launcher. The launcher selects a release by its pinned version and verifies new downloads against their SHA-256 checksums. Each release has its own cache entry, so updates can reuse a previously verified download.
