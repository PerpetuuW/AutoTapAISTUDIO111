
### [2026-10-15 16:33:28] Комплексное исправление ошибок аудита ветки 1Gemini
- Устранены операторы !! в MacroExecutionEngine.kt и TemplateNoiseStressCalibrationTestSuite.kt (G-02).
- Восстановлен роутинг action.isNeuralEngine -> NeuralVisualMatcher.findMatches и 60 FPS стриминг MediaProjection.
- Устранена коллизия повторного addView(inputLayout) в EditActionDialog.kt и жесткая обрезка текста .take().
- Синхронизированы пороги NeuralVisualMatcher (+5% буфер и 98% порог) и мультипоиск в VisionGatewayImpl.
- В ControlPanelComposeOverlay возвращены кнопки Графа, Пошаговой отладки и Журнала логов.
- Восстановлен anchor_inspector.py и удалены .bak файлы.

### [2026-10-15 17:01:58] Углубленный аудит и оптимизация поиска, калибровки, эргономики и графов (1Gemini)
- Поиск и калибровка: исправлены координаты baseAnchorX/Y и коллизия кэша в SmartMaskEngine, синхронизирован кроп raw_*.png с mask_*.png, исправлена запись центра calibratedX/Y (clickX/clickY), разблокирован живой ползунок порога при перекалибровке и подключен sampleStride от downscaleFactor.
- Эргономика: подключено сворачивание карточки calibrationContentLayout, обновлено превью ЭКРАН 1:1 при выборе альтернативного кандидата, центрирована зона +ROI, устранены коллизии addView(inputLayout) и .take(), возвращены кнопки Графа/Отладки/Логов в Compose-пульте.
- Графы: исправлены порты out_timeout/out_match_trig в Wire-to-Empty, хитбокс входного порта (headerHeight/2f), кэширование превью в GraphCanvasView.onDraw, степпер цикла (+/-), сериализация TriggerBehavior и мультишаблоны в evaluateSingleGraphTrigger.
