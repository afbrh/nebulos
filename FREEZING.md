# Freezing a Switch game's runtime

All Switch cores (smo, dread, mp4, sshd, wonder, sm3dw, las, eow) share one native runtime, `../smorecomp-src` (built by `:core-smo`'s CMake). A
**frozen** game loads its own copy of that runtime, built from a git tag, so later runtime edits for another game cannot regress it.

How it works
- `frozen-runtimes.properties`: `game = tag` (game = core name). Games not listed use the live working tree (`libsmo_main.so`).
- Each tag has a git worktree at `../smorecomp-frozen/<tag>` (outside smorecomp-src; never touches its working tree).
- `core-smo` CMake builds one extra library per tag, `libsmo_main_<tag with - as _>.so`, from that worktree. SDL static, shaderc, FFmpeg and
  libadrenotools always come from the live tree (build deps, not runtime source).
- The game's activity returns `RuntimeLib.forGame("<game>")` from `getLibraries()`: the frozen lib, else `smo_main`. (Each game has its own process,
  so only one runtime is loaded.)
- Still shared, NOT frozen: the Java glue (`SMORecompAndroidActivity`, compiled from the live tree), `recomp-common` (SDL, vpad) and the core modules.
  A change there can still affect a frozen game; keep such changes backward compatible.

## Current state: ONE shared frozen runtime (2026-11-23)

Every Switch game (smo, dread, sm3dw, wonder, las, eow, sshd, mp4) loads the same frozen runtime, tag **`shared-v1`** (smorecomp-src 68a29dd), worktree
`../smorecomp-frozen/shared-v1`, one library `libsmo_main_shared_v1.so` (no live `libsmo_main.so` in the APK unless a game is thawed).
The older per-game tags (frozen-smo-v1, frozen-dread-v1, frozen-sm3dw-v1, frozen-wonder-v1/v2) stay in git for reference but are no longer used or built.

**Rule:** the runtime is changed only by thawing (`-Pnebulos.thaw=<games>` or removing lines), testing, then retagging a new shared version
(`shared-v2`): every Switch game must be retested before the new tag is mapped (smoke test = boots to gameplay, same look, similar fps).
A game-specific fix must be gated by game id so it cannot change another game.

Verified on the Nova 2026-11-23 on the live tree at 68a29dd (= shared-v1), baseline = the old per-game frozen builds:

| game | baseline (old frozen build) | on shared runtime | result |
|---|---|---|---|
| smo | title, 60 fps, ~14.7k draws | title identical, Resume -> Cap Kingdom gameplay, 60 fps (152k draws) | pass |
| dread | title, 60 fps, 840 draws | title identical, Samus files, New Game opening text; 60 fps UI, 30 fps cutscene | pass |
| sm3dw | title, 60 fps | chooser -> Bowser's Fury loading -> gameplay (Mario in the rain), 60 fps (83k draws) | pass |
| wonder | title, 60 fps | title identical, character select, world map with 3D models (TEX.B ok), 60 fps | pass |
| las | live (unchanged) | title, file select, Autosave -> Mabe Village, 60 fps | pass |
| sshd | live (unchanged) | boots to the opening-movie parchment, 30 fps; the known opening-movie stall (paused) | same as before |
| eow | live (unchanged) | title; the castle/town work was verified in the previous round | pass |

Screenshots/logs: `../eow-research/shared/` (`base-*` = old frozen build, `live-*` = shared runtime; scripts `go.sh`, `seq.sh`).

Each game's Activity returns `RuntimeLib.forGame("<core>")`; `frozen-runtimes.properties` maps every core to `shared-v1`.

## Freeze a game
1. Pick the commit where the game was verified; tag it: `git -C ../smorecomp-src tag -a frozen-<game>-v1 <commit> -m "what was verified"`.
2. Make the checkout: `git -C ../smorecomp-src worktree add --detach ../smorecomp-frozen/frozen-<game>-v1 frozen-<game>-v1`
   (does not touch the live tree; if worktrees misbehave: `git -C ../smorecomp-src archive <tag> --prefix=frozen-<game>-v1/ | tar -x -C ../smorecomp-frozen`).
3. Add `<game>=frozen-<game>-v1` to `frozen-runtimes.properties`.
4. In the game's Activity add
   `override fun getLibraries(): Array<String> = com.noryan.nebulos.core.smo.RuntimeLib.forGame("<game>")` (smo, dread, wonder, sm3dw have it already).
5. Build (take `/Users/ry/Desktop/claude/.build-lock` first): `./gradlew -Pnebulos.cores=<game> :core-<game>:assembleDebug`.

Several games may share one tag (then they get separate but identical libraries; separate tags let you fix one without the other).

## Thaw (back to the live tree)
- One build: `-Pnebulos.thaw=wonder,dread` builds those games from the live tree for that invocation.
- For good: delete the game's line in `frozen-runtimes.properties`. (Optionally `git worktree remove ../smorecomp-frozen/<tag>`; keep the tag.)

## Fix a frozen game (cherry-pick into a tag)
A tag is immutable; make a new one.
```
cd ../smorecomp-frozen/frozen-wonder-v1          # the worktree is a detached HEAD
git cherry-pick <fix-commit>                      # fix commit from the live branch (or commit a hand fix here)
git tag -a frozen-wonder-v2 -m "v1 + <fix>"
cd ../../smorecomp-src && git worktree add --detach ../smorecomp-frozen/frozen-wonder-v2 frozen-wonder-v2
```
Then point `wonder=` at `frozen-wonder-v2` and `git worktree remove ../smorecomp-frozen/frozen-wonder-v1` once v2 is verified. Commits made in a worktree land in
the same repository, so they are reachable by tag only: always tag before removing a worktree.

## Notes
- `-Pnebulos.cores=<list>` only builds the frozen libs of listed games, and the live lib only if a listed Switch game is not frozen.
- The tags and worktrees are local only (smorecomp-src is never pushed). `../smorecomp-frozen/` is not a git repo; it is rebuilt from the tags with step 2.
- Verifying a freeze: `unzip -l` the APK for `lib/arm64-v8a/libsmo_main_<tag>.so`; the activity logs `Running main function SDL_main from library .../libsmo_main_<tag>.so`.
