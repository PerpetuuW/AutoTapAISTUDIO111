
### [2026-10-15 16:33:28] Комплексное исправление ошибок аудита ветки 1Gemini
- Устранены операторы !! в MacroExecutionEngine.kt и TemplateNoiseStressCalibrationTestSuite.kt (G-02).
- Восстановлен роутинг action.isNeuralEngine -> NeuralVisualMatcher.findMatches и 60 FPS стриминг MediaProjection.
- Устранена коллизия повторного addView(inputLayout) в EditActionDialog.kt и жесткая обрезка текста .take().
- Синхронизированы пороги NeuralVisualMatcher (+5% буфер и 98% порог) и мультипоиск в VisionGatewayImpl.
- В ControlPanelComposeOverlay возвращены кнопки Графа, Пошаговой отладки и Журнала логов.
- Восстановлен anchor_inspector.py и удалены .bak файлы.
