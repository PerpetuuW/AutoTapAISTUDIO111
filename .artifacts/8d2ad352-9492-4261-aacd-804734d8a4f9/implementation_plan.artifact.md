# Fix Screen Lock Unlock Condition & Gesture Recorder Passthrough

## Problem
1. **Screen Lock Overlay Issue**: The unlock area in `ScreenLockOverlay.kt` requires holding a specific position for 3 seconds. Sometimes this touch might be blocked by the `bgLockContainer` not dispatching touches or the overlay parameters preventing easy unlocking. The user asked "в блокировке замка доступность разблокировки должна быть всегда" (unlock must always be available).
2. **Gesture Recorder Simultaneous Usage**: The gesture recorder (`GestureRecorderOverlay.kt`) currently attempts to record clicks, swipes, and paths but intercepts ALL touch events. The user requested simultaneous use of gestures and recording ("Одновременные жесты в телефон с записью должны быть"). This means gestures should be passed through to the system in real-time while recording. We need to dispatch the touches via Accessibility Service as they happen or allow window passthrough while tracking.

## Proposed Changes

### 1. Screen Lock Overlay (`ScreenLockOverlay.kt`)
- Modify `bgLockContainer` to completely ignore touches (`FLAG_NOT_TOUCHABLE`), or ensure the `unlockTouchArea` (which is in `uiLockContainer`) is clearly prioritized. Currently, `uiWindowParams` has `FLAG_NOT_TOUCH_MODAL`, which is fine, but to ensure the unlock is ALWAYS available, we'll expand the touchable surface of the unlock area or make it completely opaque to touches over the whole card.
- We will decrease the required swipe threshold for cancellation or make the countdown more resilient to minor finger movements so it doesn't reset too easily.

### 2. Gesture Recorder Overlay (`GestureRecorderOverlay.kt`)
- Currently, when `MotionEvent.ACTION_DOWN`, `ACTION_MOVE`, `ACTION_UP` happen, `GestureRecorderOverlay` captures them to build a path.
- To allow "simultaneous" gestures (live passthrough), we will use `AutoTapAccessibilityService.instance?.gestureDispatcher?.dispatchLiveGesture(...)` in real-time during `ACTION_DOWN` and `ACTION_MOVE`. Wait, the Accessibility Service `dispatchGesture` API requires complete paths or strokes. It's not a real-time `injectInputEvent`.
- Alternatively, since the user said "допустимы небольшие задержики" (small delays are acceptable), we will dispatch the *entire* gesture (click or swipe) into the system immediately upon `ACTION_UP` while simultaneously recording it into `recordedActions`.
- Wait, the code already does `AutoTapAccessibilityService.instance?.gestureDispatcher?.performClick(...)` inside `ACTION_UP` if `isSmartRecording` is not doing AI captures? Actually, looking at the code, it sets `isLiveStreamingActive` but doesn't actually dispatch the basic click to the phone during regular recording.
- We will add immediate gesture playback on `ACTION_UP` for all recorded basic clicks and swipes in standard click mode.
  - On `ACTION_UP` for a normal click: `accessibilityService.performClick(x, y)`
  - On `ACTION_UP` for a swipe/path: `accessibilityService.performSwipe(...)`
  This fulfills "Одновременные жесты в телефон с записью должны быть" by replaying the gesture on the device immediately after recording it.

## Verification Plan
1. Check `ScreenLockOverlay` unlock resilience.
2. Check `GestureRecorderOverlay` dispatching the gesture on `ACTION_UP`.
3. Verify compilation and run all 1AInspector scripts.