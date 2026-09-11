# Changelog

Notable changes to Spybot, newest first. Every pull request adds one bullet at the top
of this list (CI enforces this). Keep each bullet on a single line.

Nothing else goes in this file - no headings, versions, dates or commit hashes. At build
time the changelog page groups these bullets by the master commit that added them, using
git: each group shows the commit's short hash and date, and its tag if the commit has one.
So a release is simply a tag on master.

- Required a changelog entry on every pull request via a CI check, and grouped the changelog page by the master commit that added each entry (#209)
- Added a Steam name generator page that puns famous people's names with Counter-Strike terms, matched by pronunciation rather than spelling (#208)
- Attached the Sentry OpenTelemetry agent for per-operation tracing spans, including individual SQL queries (#207)
- Enabled Sentry's Logs product so application logs are forwarded to Sentry, not only error events (#206)
- Fixed Sentry crashing both apps at startup on Spring Boot 4 by switching to the Spring Boot 4 Sentry module (#205)
- Fixed TeamSpeak channel ordering to follow the server's real sibling order instead of sorting channel_order as a plain number (#204)
- Fixed Flyway migrations not running at all on Spring Boot 4 (#204)
- Added Sentry.io error and log reporting to spybot-web and spybot-recorder (#203)
- Sped up Docker image CI by building natively on an arm64 runner with GitHub Actions layer caching (#202)
- Polished the user page statistics card, replaced the navbar theme toggle with a Light/Dark/System picker, fixed nav icons hidden at tablet widths, and added a users-this-week/today tile to the home page (#201)
- Fixed the recorder's clientlist query missing the dash on its -uid flag, which made every recorder restart drop still-connected users from the live view (#200)
- Fixed racing deploys so a newer commit on master can no longer be overwritten by an older, slower build (#199)
- Fixed the recorder reconnecting every ~5 minutes by sending a periodic keepalive, and a stale-session cleanup that could drop a still-connected user from the live view (#198)
- Let browsers cache static assets for a day instead of refetching icons, scripts and styles on every page load (#197)
- Fixed the recorder using the wrong jOOQ SQL dialect, which broke channel sync on every connection attempt (#196)
- Fixed the Caddyfile bind mount on deploy and removed orphaned containers left over from the pre-rewrite stack (#195)
- Published both the spybot-web and spybot-recorder Docker images to GHCR and made deploys pull the exact commit's images instead of building on the server (#193, #194)
- Rewrote Spybot on Spring Boot + Kotlin (spybot-core, spybot-web, spybot-recorder), alongside the existing Django app
- Added the admin interface (dashboard, merged users, TS users, news events, merge users)
- Fixed TeamSpeak channel/username display so escaped ServerQuery characters render correctly
- Fixed the recorder hanging on TS3's "\n\r" line terminator, with exponential backoff on reconnect failures
- Added Steam ID validation and a linked-account modal to the profile page
- Last release of the original Python/Django Spybot, before the Spring Boot + Kotlin rewrite began
