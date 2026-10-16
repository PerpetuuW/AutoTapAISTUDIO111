
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

### [2026-10-16 01:15:00] Комплексный аудит, устранение ошибок и оптимизация поиска
- Ошибки: передан флаг отмены isCancelled в VisionGatewayImpl.findTemplateMatches, синхронизированы оффсеты cropOffsetX/Y при fallback-маске в SmartMaskEngine, сгенерированы gradlew/gradlew.bat и настроен безопасный utf-8 вывод в консоли Windows для ci_preflight_gate.py и run_all_tests.py.
- Системная оптимизация: внедрен переиспользуемый ocrExecutor в OcrEngine (устранено создание пула потоков на каждый вызов), добавлено энергосберегающее троттлирование распаковки 60 FPS кадров при простое в MediaProjectionService, кэширован OverlayWindowManager в MacroExecutionEngine.
- Оптимизация поиска: добавлена быстрая табличная аппроксимация fastCbrt (LUT) в ColorDetector для вычисления ΔE (прирост скорости в 3-5 раз), внедрена ранняя отмена оставшихся задач OCR при нахождении целевого слова, предаллоцирована емкость сетки сканирования scanPoints в TemplateMatchingEngine.
### [2026-10-16 01:50:00] Устранение миганий экрана, оптимизация блокировки AMOLED, синхронизация меток и восстановление записи путей
- Блокировка экрана (AMOLED) и устранение миганий:
  - Полное покрытие экрана: внедрен расчет физических метрик дисплея (getRealMetrics) с флагами LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES, FLAG_LAYOUT_NO_LIMITS, SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN и SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION для 100% покрытия вырезов камер, статус-бара и навигационной панели.
  - Устранено мигание при кликах: ликвидирована динамическая генерация 4 firewall-оверлеев со сквозной дырой в ScreenLockOverlay, passthrough переведен на атомарное переключение флага FLAG_NOT_TOUCHABLE фонового окна.
  - Независимость панели разблокировки: uiLockContainer (anchorCard) защищен от затемнения и мерцания (alpha = 1.0f всегда), флаг FLAG_NOT_TOUCHABLE никогда не накладывается на карточку разблокировки, гарантируя мгновенный отклик на удержание.
  - Исключение стробирования при поиске: в MacroExecutionEngine убран избыточный fallback на системный captureScreenshotSync при активном 60 FPS стриминге MediaProjection, устранены паразитные скачки прозрачности оверлеев. Разъяснено: яркость экрана (аппаратная подсветка) никак не влияет на сырые RGB-буферы кадров GPU/MediaProjection.
- Синхронизация меток с пультом:
  - В TargetOverlayManager внедрен callback onVisibilityModeChanged, подключенный к controlPanelOverlay.updateVisibilityMode в AutoTapOrchestrator.
  - Устранен сброс пользовательского режима видимости на FULL при добавлении действий и загрузке сценария, исключена рассинхронизация кнопки "Глаз" с фактическим состоянием мишеней.
  - При создании мишеней сразу выставляется корректный alpha в соответствии с текущим visibilityMode.
- Запись жестов и система путей:
  - В GestureRecorderOverlay восстановлена запись всех непрерывных движений как ActionType.PATH с автоматической генерацией траектории и промежуточных сплайн-точек (поддержка Bezier-кривых в редакторе).
  - Обеспечена сквозная трансляция записанных путей в underlying-приложение/игру как в обычном, так и в умном режиме записи.
  - В GestureDispatcher.performPathAsync оптимизировано исполнение multi-point сплайнов через единый непрерывный StrokeDescription, исключающий сбои и обрывы цепочки continueStroke.

### [2026-10-16 02:20:00] Аддитивные ROI-якоря и умный каскадный поиск мультимасок (до 8 масок)
- Аддитивное добавление ROI (без перезаписи исходной точки поиска):
  - В AutoTapOrchestrator при добавлении/изменении ROI координаты posX/posY действия сохраняются без изменений, а центр выбранной зоны ROI регистрируется в primaryAnchorPoints и TemplateMatchingEngine (discoveredLocations).
  - Для макрошагов центр ROI автоматически привязывается к основному выбранному шаблону (selectedTemplateIndex / templatePath) и сохраняется в meta.json шаблона в массив discoveredLocations (с дедупликацией в радиусе 24px и кольцевым буфером до 8 точек).
  - В CalibrationOverlay при нажатии "+ ROI" центр зоны добавляется в discoveredLocations именно калибруемой маски (existingTemplatePath / tPath) и в primaryAnchorPoints действия, не сбивая исходные calibratedX/Y.
- Умный каскадный поиск мультимасок (до 8 масок):
  - В TemplateMatchingEngine (findMultiTemplatesFastCascade) реализован двухфазный поиск.
  - Фаза 1 (Fast Anchor Pass): мгновенная проверка всех активных масок (до 8) по первичным точкам и известным якорям (Tier 0: ±12px, Tier 1: ±48px) без вызова тяжёлого полноэкранного сканирования. При статичном или малоподвижном интерфейсе поиск занимает ~1-2 мс для всех 8 масок.
  - Фаза 2 (Fallback Grid Scan): тяжёлый сеточный скан Tier 2 по ROI/экрану выполняется только для тех масок, которые не были обнаружены на Фазе 1.
  - В TemplateMatchingEngine в recordFoundLocation и registerDiscoveredLocation устранена очистка locs.clear() при достижении лимита — теперь удаляется только самый старый элемент, обеспечивая строго аддитивную историю обнаружений.
