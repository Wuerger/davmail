# Wuerger/davmail Workspace Context & Agent Memory

## 1. Project Overview
- **Repository**: `Wuerger/davmail` (Fork of upstream [`mguessan/davmail`](https://github.com/mguessan/davmail)).
- **Primary Use Case**: Headless IMAP proxy running in Docker on an LXC container (Proxmox host) for an `Outlook.com` consumer mailbox via Microsoft Graph REST API.
- **Client Integration**: Paperless-ngx automated document ingestion connecting via IMAP with tag/category translation (`davmail.imapFlags.<FLAG>=<CATEGORY>`).

---

## 2. Key Architecture & Custom Enhancements

### A. Client-Side IMAP `UNKEYWORD` / `NOT KEYWORD` Filtering
- **Problem**: Microsoft Graph OData search filters with `not categories/any(c: c eq 'Category')` silently omit uncategorized messages (messages with null or empty category collections).
- **Solution**: Handled client-side in [`ImapConnection.java`](file:///home/morgan/git/Wuerger/davmail/src/java/davmail/imap/ImapConnection.java) via `matchesKeywords()`. Uncategorized and non-matching emails are successfully returned to IMAP clients such as Paperless-ngx.
- **Tests**: [`TestSearchKeywords.java`](file:///home/morgan/git/Wuerger/davmail/src/test/davmail/imap/TestSearchKeywords.java).

### B. Graph Search Filtering (`FROM`, `TO`, `CC`, `BCC`, `MESSAGE-ID`)
- **`FROM`**: Mapped as native Graph properties `from/emailAddress/address` and `from/emailAddress/name` in [`GraphExchangeSession.java`](file:///home/morgan/git/Wuerger/davmail/src/java/davmail/exchange/graph/GraphExchangeSession.java).
- **`TO`, `CC`, `BCC`**: Mapped to MAPI extended properties:
  - `to` / `displayto`: `0x0E04` (`PR_DISPLAY_TO`)
  - `cc` / `displaycc`: `0x0E03` (`PR_DISPLAY_CC`)
  - `bcc` / `displaybcc`: `0x0E02` (`PR_DISPLAY_BCC`)
- **Tests**: [`TestGraphSearchFilter.java`](file:///home/morgan/git/Wuerger/davmail/src/test/davmail/exchange/graph/TestGraphSearchFilter.java).

### C. MAPI PropertyTag Format & UID Deserialization
- **Canonical Format**: Microsoft Graph canonicalizes extended property IDs to lowercase hex without leading zeros (e.g. `String 0xe04`, `Integer 0xe23`).
- **Formatting Rule**: In [`GraphField.java`](file:///home/morgan/git/Wuerger/davmail/src/java/davmail/exchange/graph/GraphField.java), `propertyTag` must always be formatted as `"0x" + Integer.toHexString(intPropertyTag)` (do not use `0x%04X`).
- **Resilient Matching**: Extended property IDs in Graph JSON responses must be matched using `.equalsIgnoreCase(responseId)` in [`GraphExchangeSession.java`](file:///home/morgan/git/Wuerger/davmail/src/java/davmail/exchange/graph/GraphExchangeSession.java) and [`GraphObject.java`](file:///home/morgan/git/Wuerger/davmail/src/java/davmail/exchange/graph/GraphObject.java) to prevent `imapUid` from defaulting to `0`.

### D. Docker & Logging Behavior
- **Container Detection**: [`Settings.isDocker()`](file:///home/morgan/git/Wuerger/davmail/src/java/davmail/Settings.java) checks for `/.dockerenv` or `/run/.containerenv`.
- **Stdout Logging**: By upstream design, `FileAppender` is disabled in Docker (`if (!isDocker())`). All log events stream to `ConsoleAppender` (container `stdout`). Logs should be inspected via `docker compose logs -f` or `docker logs <container>`.
- **Token Persistence**:
  - `davmail.oauth.tokenFilePath=/config/tokens.properties` stores refresh tokens in a dedicated file.
  - When `tokenFilePath` is configured, DavMail reads and writes tokens exclusively to that file. Do not keep redundant `davmail.oauth.<user>.refreshToken` lines in `davmail.properties`.

---

## 3. Upstream Sync & PR Tracking
- **Upstream Git Remote**: `upstream` -> `https://github.com/mguessan/davmail.git`
- **Origin Git Remote**: `origin` -> `https://github.com/Wuerger/davmail.git`
- **Existing PR Branches**:
  - `feature/imap-search-fixes`: `UNKEYWORD` and `INTERNALDATE` search fixes.
  - `feature/graph-sender-recipient-search`: `FROM`, `TO`, `CC`, `BCC` search filter support.
- **Upstream Merge Status**: Merged up to upstream `master` commit `a5503367` (includes RFC 4315 `UID EXPUNGE` and folder refresh improvements).

---

## 4. CI/CD & Build Pipelines
- **Workflow**: [`.github/workflows/docker.yml`](file:///home/morgan/git/Wuerger/davmail/.github/workflows/docker.yml)
- **Trigger**: Any push to `master` or tags.
- **Target Registry**: GitHub Container Registry (`ghcr.io/wuerger/davmail`)
- **Tags Generated**: `latest`, `7.0.0-wuerger`, `sha-<commit>`.
- **Platform**: `linux/amd64` (due to OpenJFX Debian packaging dependencies).

---

## 5. Local Development & Testing Commands
```bash
# Ant build (standard DavMail packaging)
ant -Dfile.encoding=UTF-8 jar

# Unit tests
ant test
```
