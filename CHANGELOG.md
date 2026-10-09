# Changelog

Notable changes to Spybot, newest first. Add an entry here in the same PR as any
user-visible or otherwise notable change.

Format (each bullet must be a single line - no wrapping, the parser doesn't join
continuation lines):

```
## <version> - <YYYY-MM-DD> (<commit hash>)
- <notable change>
- <notable change>
```

## v3.0.0-beta.2 - 2026-10-09 (99274f2)
- Fixed the Recent activity chart's day labels: they now sit centered under each bar and adapt to the available width (weekday and day, stacked initial over day on phones, or thinned dates for long spans)
- Tightened the chart cards on phones and removed dead space under the time-of-day histogram

## v3.0.0-beta.1 - 2026-08-27 (72c2bf1)
- Rewrote Spybot on Spring Boot + Kotlin (spybot-core, spybot-web, spybot-recorder), alongside the existing Django app
- Added the admin interface (dashboard, merged users, TS users, news events, merge users)
- Fixed TeamSpeak channel/username display so escaped ServerQuery characters render correctly
- Fixed the recorder hanging on TS3's "\n\r" line terminator, with exponential backoff on reconnect failures
- Added Steam ID validation and a linked-account modal to the profile page

## v2.4 - 2026-04-12 (1492588)
- Last release of the original Python/Django Spybot, before the Spring Boot + Kotlin rewrite began
