# "Check for updates" on the home screen (Google Play In-App Updates)

Date: 2026-11-27. David asked for a way to see and get a new build from inside TypeBetter, after build
147 reached the internal test track but had not shown on his phone yet.

## Decision

- A **Check for updates** row in "Everything else" on the home screen, above About
  (`com.typebetter.keyboard.settings.CheckForUpdatesRow`). It asks Google Play when the screen opens and
  shows the answer as the row's summary: the build he has (the GitHub run number, version code − 142000),
  and whether a newer one is on Play.
- Installing uses Play's In-App Updates (`com.google.android.play:app-update`). An **immediate** update
  is preferred: Play shows its own progress screen and restarts TypeBetter on the new build, so nothing
  is left half done. When Play only allows a **flexible** (background) update, the row shows the download
  percentage and then a Restart button (`completeUpdate`). When Play allows neither, Update opens
  TypeBetter's Play Store page.
- An update left running (Play's screen closed mid-install) shows as "Updating…"; coming back to the
  screen checks again, and a tap resumes it, as Play's guide requires.
- A copy not installed by the Play Store (a GitHub APK, a debug build) says so, with nothing to tap,
  since Play cannot update it. A Play-installed copy that Play still refuses ("not owned" or "API not
  available": another Google account signed in to Play, or an old Play Store) gets an Update button that
  opens the Play Store page instead.
- Only the newest check may set the row (older ones are cancelled), so a late answer never overwrites
  live download progress. A failed Restart (`completeUpdate`) shows as a failed check.
- When Play says there is nothing newer, the row says a new build can take up to an hour to show up:
  Play serves the update list from a cache, which is the usual reason a just-uploaded build is missing.
- Pure state logic in `UpdateCheck.kt` with unit tests (`UpdateCheckTest`); the row only calls Play.

## Also

- `tools/play-upload.sh` now says plainly when Play kept a release back for a manual "Send changes for
  review" (the `changesNotSentForReview` fallback), as a workflow warning. Before, that case printed the
  same "Released" line as a release that went out.
- No data leaves the phone for this beyond what the Play Store app already does; no Data safety change.
