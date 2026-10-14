# Граф Зависимостей Архитектуры (AutoTap)

Данный Mermaid-граф автоматически сгенерирован 1AInspector для визуализации взаимосвязей классов внутри проекта.

```mermaid
graph TD
    classDef orchestrator fill:#312E81,stroke:#818CF8,stroke-width:2px,color:#fff;
    classDef engine fill:#3B2606,stroke:#F59E0B,stroke-width:2px,color:#fff;
    classDef overlay fill:#0F172A,stroke:#38BDF8,stroke-width:2px,color:#fff;
    classDef domain fill:#14241B,stroke:#34D399,stroke-width:2px,color:#fff;

    AbstractComposeOverlay
    class AbstractComposeOverlay overlay
    AccessibilityUtils
    ActionType
    class ActionType domain
    AppLogger
    AppProfileDetector
    AutoTapAccessibilityService
    AutoTapOrchestrator
    class AutoTapOrchestrator orchestrator
    CalibrationOverlay
    class CalibrationOverlay overlay
    CanvasIconButton
    CaptureFrameOverlay
    class CaptureFrameOverlay overlay
    ClickPulseView
    ColorDetector
    ControlPanelListener
    ControlPanelOverlay
    class ControlPanelOverlay overlay
    ControlPanelState
    CoordinateNormalizer
    DebuggerToolbarOverlay
    class DebuggerToolbarOverlay overlay
    DeviceDisplaySpecs
    EditActionDialog
    class EditActionDialog overlay
    EvaluationPolicy
    ExecutionState
    ExpressionEvaluator
    EyedropperOverlay
    class EyedropperOverlay overlay
    GestureDispatcher
    GestureRecorderOverlay
    class GestureRecorderOverlay overlay
    GestureVisualizerManager
    GlobalSettingsDialog
    class GlobalSettingsDialog overlay
    GraphCanvasView
    GraphEditorOverlay
    class GraphEditorOverlay overlay
    GraphOverlayView
    class GraphOverlayView overlay
    HumanGestureEngine
    class HumanGestureEngine engine
    IGestureGateway
    IMacroEngine
    class IMacroEngine engine
    IOverlayGateway
    class IOverlayGateway overlay
    IScenarioRepository
    class IScenarioRepository domain
    ITemplateRepository
    class ITemplateRepository domain
    IVisionGateway
    ImagePreprocessing
    InteractiveRoboticArmDemoDialog
    class InteractiveRoboticArmDemoDialog overlay
    InteractiveTutorialOverlay
    class InteractiveTutorialOverlay overlay
    KeepAliveDaemonService
    LaserScanVisualizer
    LicenseManager
    LinearToGraphMigrator
    LocalizationManager
    LogViewerDialog
    class LogViewerDialog overlay
    MacroAction
    class MacroAction domain
    MacroExecutionEngine
    class MacroExecutionEngine engine
    MacroScenario
    class MacroScenario domain
    MagicWandEditorOverlay
    class MagicWandEditorOverlay overlay
    MainActivity
    MaskPreviewView
    MatchCandidate
    MediaProjectionService
    NeuralVisualMatcher
    class NeuralVisualMatcher engine
    OcrCandidatePickerDialog
    class OcrCandidatePickerDialog overlay
    OcrEngine
    class OcrEngine engine
    OcrMatchResult
    OcrOffsetCalibrationOverlay
    class OcrOffsetCalibrationOverlay overlay
    OcrQueryMetadataManager
    OverlayLifecycleOwner
    class OverlayLifecycleOwner overlay
    OverlayVisibilityMode
    class OverlayVisibilityMode overlay
    OverlayWindowManager
    class OverlayWindowManager overlay
    PackageBackupManager
    PanelDisplayMode
    PathCompressionEngine
    class PathCompressionEngine engine
    PixelBufferPool
    Point2D
    ProjectIntegrityInspector
    RoiSelectorOverlay
    class RoiSelectorOverlay overlay
    RunningBadgeComposeOverlay
    class RunningBadgeComposeOverlay overlay
    RunningBadgeOverlay
    class RunningBadgeOverlay overlay
    SafetyGovernor
    ScenarioRepositoryImpl
    class ScenarioRepositoryImpl domain
    ScreenLockOverlay
    class ScreenLockOverlay overlay
    ScreenStabilityDetector
    ScriptsDialog
    class ScriptsDialog overlay
    SmartMaskEngine
    class SmartMaskEngine engine
    SubroutineFrame
    SubroutineManager
    SwipeTrailView
    TargetHighlightVisualizer
    TargetOverlayManager
    class TargetOverlayManager overlay
    TargetOverlayView
    class TargetOverlayView overlay
    TargetQuickRingOverlay
    class TargetQuickRingOverlay overlay
    TemplateMatchingEngine
    class TemplateMatchingEngine engine
    TemplateMorphology
    class TemplateMorphology domain
    TemplateMorphologyClassifier
    class TemplateMorphologyClassifier domain
    TemplateRepositoryImpl
    class TemplateRepositoryImpl domain
    VectorIconDrawer
    VirtualStick
    VisionGatewayImpl
    YandexAdsManager

```
