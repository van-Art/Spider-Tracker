# Phase 3.1 marker animation

## Sprite and glow separation

The original 34×34 green/red/white location PNGs are decoded once, without density resampling, then drawn with FilterQuality.None into a single Compose Canvas. Centers are projected from geographic coordinates and destinations round to physical pixels. The sprite pixels never receive a blur, tint, fade, color filter, rotation or animated scale. Clusters use the same original PNG plus a count in the existing font. The white main-point PNG appears only after a successful device-provider fix; the separate Phase3 address destination circle remains distinct.

Each kind has a separate native CircleLayer behind its PNG: places #45D26A, alerts #FF4D50, user #00D7FA. Only these CircleLayers use circleBlur=.65 and soft opacity. They share the same GeoJSON coordinates/clustering as the projected sprites, so camera pan/zoom keeps their geographic alignment. Custom Canvas sprite centers round by at most half a physical pixel; native circles retain normal geographic interpolation.

| Kind | Period | Radius progression | Opacity |
|---|---:|---|---|
| Acquired user fix | 1.8s | 16→32 logical map pixels | 0.28→0 |
| OSM place | 2.4s | 16→32 | 0.28→0 |
| Alert / clearly labeled demo | 1.6s | 16→32 | 0.28→0 |

MarkerAnimationState samples normalized elapsed time. Each pulse expands continuously and fades before repeating; the PNG stays unchanged. Native property transition durations are zero so hidden extra transitions cannot alter the chosen periods. Sampling is20Hz rather than one independent animation or Android View per point.

## Work and lifecycle

The timer runs only in Lifecycle.RESUMED and only for kinds with viewport-intersecting projected markers. Disabling a layer removes its source features and its active animation kind. Panning all markers off screen also removes that kind from the animation loop. Resume restarts the pulse phase; background time is not simulated. The supplied character still loops at12fps, but its frame changes are isolated to its own composable; radar sweep ticks redraw the radar rather than rebuilding the whole screen. Radar geographic dots are memoized until camera/data/fix changes.

System animator-duration scale0 triggers reduced motion through a Settings observer. Glows then use a static radius22/opacity0.14 without a timer; character and radar motion stop as well. The current Settings value is authoritative, avoiding the delayed ValueAnimator cache after the setting changes. Normal animation resumes when the setting is restored. Capture mode uses fixed time0.7s for repeatable local screenshots.

Native clustered features are queried only after a completed rendering frame and while the renderer is active. Camera callbacks reproject cached coordinates, avoiding a native query before the renderer exists. Pause/disposal invalidate readiness and delayed callbacks. Source changes, a source-idle refresh and the first fully loaded viewport frame schedule bounded POI/projection refreshes rather than rescanning POI data on every pulse tick. The idle refresh and six coalesced source-change checks at150ms intervals capture asynchronous cluster rebuilds that finish after an earlier frame. These checks are bounded to0.9s, run only while active, and coalesce changes from the three sources; there is no permanent source polling. Selected place panels follow stable IDs when tile metadata is refreshed.

## Validation scope

Unit checks assert each period, expansion/fade, repetition, reduced-motion constancy and coordinate-based radar behavior. Android tests check marker projection against MapLibre coordinates, real native glow changes, disabled-layer stopping, system reduced-motion behavior, background pause/resume and dense clustering. See phase-3-1-test-results.md for the final executed outcomes. No physical-device battery/power calibration or sustained FPS claim is made.
