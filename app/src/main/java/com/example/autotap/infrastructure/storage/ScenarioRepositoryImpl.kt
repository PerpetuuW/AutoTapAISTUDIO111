package com.example.autotap.infrastructure.storage

import android.content.Context
import com.example.autotap.core.logger.AppLogger
import com.example.autotap.core.math.CoordinateNormalizer
import com.example.autotap.core.math.LinearToGraphMigrator
import com.example.autotap.domain.model.ActionType
import com.example.autotap.domain.model.DeviceDisplaySpecs
import com.example.autotap.domain.model.MacroAction
import com.example.autotap.domain.model.MacroScenario
import com.example.autotap.domain.model.Point2D
import com.example.autotap.domain.model.graph.EvaluationPolicy
import com.example.autotap.domain.model.graph.GraphMacroScenario
import com.example.autotap.domain.model.graph.NodeActionSpec
import com.example.autotap.domain.model.graph.NodeTriggerSpec
import com.example.autotap.domain.model.graph.ScenarioEdge
import com.example.autotap.domain.model.graph.ScenarioNode
import com.example.autotap.domain.repository.IScenarioRepository
import java.io.File
import java.io.FileOutputStream
import java.util.LinkedHashMap
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

class ScenarioRepositoryImpl(private val context: Context) : IScenarioRepository {

    private val scriptsDir: File
        get() = File(context.filesDir, "scripts").apply { mkdirs() }

    override fun normalizeScenarioName(rawName: String): String {
        return File(rawName.trim()).name
            .replace(Regex("[^A-Za-z0-9А-Яа-яЁё._-]"), "_")
            .trim('.', ' ')
            .take(80)
            .ifEmpty { "Сценарий_${System.currentTimeMillis() % 1000}" }
    }

    private fun resolveTemplatePath(rawPath: String): String {
        if (rawPath.isBlank()) return ""
        val rawFile = File(rawPath)
        if (rawFile.exists()) return rawFile.absolutePath

        val baseTemplatesDir = File(context.filesDir, "templates").apply { mkdirs() }
        val fileName = rawFile.name

        val found = baseTemplatesDir.walkTopDown().firstOrNull { it.isFile && it.name == fileName }
        if (found != null && found.exists()) {
            return found.absolutePath
        }
        return rawPath
    }

    override fun saveScenario(scenario: MacroScenario): Boolean {
        return try {
            val safeName = normalizeScenarioName(scenario.name)
            val rootArray = JSONArray()

            val devSpecs = JSONObject().apply {
                put("screenWidth", scenario.deviceSpecs.screenWidth)
                put("screenHeight", scenario.deviceSpecs.screenHeight)
                put("densityDpi", scenario.deviceSpecs.densityDpi)
                put("density", scenario.deviceSpecs.density.toDouble())
                put("isLandscape", scenario.deviceSpecs.isLandscape)
            }
            rootArray.put(JSONObject().apply {
                put("isHeaderSpecs", true)
                put("version", scenario.version)
                put("globalClickDurationMs", scenario.globalClickDurationMs)
                put("globalSwipeDurationMs", scenario.globalSwipeDurationMs)
                put("deviceSpecs", devSpecs)
            })

            for (action in scenario.actions) {
                val actionObj = JSONObject().apply {
                    put("id", action.id)
                    put("type", action.type.name)
                    put("x", action.posX.toDouble())
                    put("y", action.posY.toDouble())
                    action.endX?.let { put("endX", it.toDouble()) }
                    action.endY?.let { put("endY", it.toDouble()) }
                    put("delay", action.delayMs)
                    put("holdDuration", action.holdDurationMs)
                    put("checkInterval", action.checkIntervalMs)
                    put("repeatCount", action.repeatCount)
                    put("randomRadius", action.randomRadiusPx)
                    put("pinchStartDistance", action.pinchStartDistance.toDouble())
                    put("pinchEndDistance", action.pinchEndDistance.toDouble())
                    put("selectedTemplateIndex", action.selectedTemplateIndex)
                    put("templatePath", action.templatePath)
                    put("similarityPercent", action.similarityPercent)
                    put("aiTimeoutSeconds", action.aiTimeoutSeconds)
                    put("clickAiTarget", action.clickAiTarget)
                    put("isWaitUntilMode", action.isWaitUntilMode)
                    put("scanStepPreset", action.scanStepPreset)
                    put("isContourMode", action.isContourMode)
                    put("isFastMode", action.isFastMode)
                    put("isMultiScaleMode", action.isMultiScaleMode)
                    put("colorDeltaEMode", action.colorDeltaEMode)
                    put("isShapeOnlyMode", action.isShapeOnlyMode)
                    put("shapeExpansion", action.shapeExpansion)
                    put("paddingOffsetPx", action.paddingOffsetPx)
                    put("isCircleShape", action.isCircleShape)
                    put("useCustomClickOffset", action.useCustomClickOffset)
                    put("clickOffsetX", action.clickOffsetX.toDouble())
                    put("clickOffsetY", action.clickOffsetY.toDouble())
                    action.jumpToStepOnMatch?.let { put("jumpToStepOnMatch", it) }
                    action.jumpToStepOnTimeout?.let { put("jumpToStepOnTimeout", it) }
                    put("targetScriptToLoad", action.targetScriptOrQuery)
                    put("subroutineTag", action.subroutineTag)
                    put("subroutineTarget", action.subroutineTarget.ifEmpty { action.targetScriptOrQuery.ifEmpty { action.subroutineTag } })

                    put("targetColorHex", action.targetColorHex)
                    put("colorTolerance", action.colorTolerance)
                    put("notifyOnMatch", action.notifyOnMatch)
                    action.roiLeft?.let { put("roiLeft", it) }
                    action.roiTop?.let { put("roiTop", it) }
                    action.roiRight?.let { put("roiRight", it) }
                    action.roiBottom?.let { put("roiBottom", it) }

                    val mArr = JSONArray()
                    action.multiTemplateIndices.forEach { mArr.put(it) }
                    put("multiTemplateIndices", mArr)

                    val mPathsArr = JSONArray()
                    action.multiTemplatePaths.forEach { mPathsArr.put(it) }
                    put("multiTemplatePaths", mPathsArr)

                    if (action.pathPoints.isNotEmpty()) {
                        val pArr = JSONArray()
                        for (pt in action.pathPoints) {
                            pArr.put(JSONObject().apply {
                                put("x", pt.x.toDouble())
                                put("y", pt.y.toDouble())
                            })
                        }
                        put("pathPoints", pArr)
                    }

                    if (action.primaryAnchorPoints.isNotEmpty()) {
                        val aArr = JSONArray()
                        for (pt in action.primaryAnchorPoints) {
                            aArr.put(JSONObject().apply {
                                put("x", pt.x.toDouble())
                                put("y", pt.y.toDouble())
                            })
                        }
                        put("primaryAnchorPoints", aArr)
                    }
                }
                rootArray.put(actionObj)
            }

            val targetFile = File(scriptsDir, "$safeName.json")
            FileOutputStream(targetFile).use { it.write(rootArray.toString().toByteArray()) }
            AppLogger.log(context, "STORAGE", "Сценарий '$safeName' успешно сохранен (${scenario.actions.size} шагов)")
            true
        } catch (e: Exception) {
            AppLogger.logError(context, "STORAGE", e)
            false
        }
    }

    override fun loadScenario(name: String): MacroScenario? {
        return try {
            val safeName = normalizeScenarioName(name)
            val file = File(scriptsDir, "$safeName.json")
            val graphFile = File(scriptsDir, "$safeName.graph.json")

            if (!file.exists() && graphFile.exists()) {
                val graph = loadGraphScenario(name)
                if (graph != null) {
                    return LinearToGraphMigrator.graphToLinear(graph)
                }
            }

            if (!file.exists()) return null

            val jsonArray = JSONArray(file.readText())
            val dm = context.resources.displayMetrics
            val curSpecs = DeviceDisplaySpecs(
                screenWidth = dm.widthPixels, screenHeight = dm.heightPixels,
                densityDpi = dm.densityDpi, density = dm.density, isLandscape = dm.widthPixels > dm.heightPixels
            )

            var srcSpecs = curSpecs
            var startIndex = 0
            var globalClick = 120L
            var globalSwipe = 300L
            var version = 1

            if (jsonArray.length() > 0 && jsonArray.getJSONObject(0).optBoolean("isHeaderSpecs", false)) {
                startIndex = 1
                val headerObj = jsonArray.getJSONObject(0)
                version = headerObj.optInt("version", 1)
                globalClick = headerObj.optLong("globalClickDurationMs", 120L)
                globalSwipe = headerObj.optLong("globalSwipeDurationMs", 300L)

                val devSpecsObj = headerObj.optJSONObject("deviceSpecs")
                if (devSpecsObj != null) {
                    val sW = devSpecsObj.optInt("screenWidth", curSpecs.screenWidth)
                    val sH = devSpecsObj.optInt("screenHeight", curSpecs.screenHeight)
                    val sDpi = devSpecsObj.optInt("densityDpi", curSpecs.densityDpi)
                    val sDensity = devSpecsObj.optDouble("density", curSpecs.density.toDouble()).toFloat()
                    val sLand = devSpecsObj.optBoolean("isLandscape", sW > sH)
                    srcSpecs = DeviceDisplaySpecs(sW, sH, sDpi, sDensity, sLand)
                }
            }

            val transform = CoordinateNormalizer.calculateTransform(srcSpecs, curSpecs)
            val loadedActions = ArrayList<MacroAction>()

            for (i in startIndex until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val type = try { ActionType.valueOf(obj.optString("type", "CLICK")) } catch (_: Exception) { ActionType.CLICK }

                val origX = obj.optDouble("x", 500.0).toFloat()
                val origY = obj.optDouble("y", 500.0).toFloat()
                val mappedPos = CoordinateNormalizer.mapPoint(Point2D(origX, origY), transform, curSpecs.screenWidth, curSpecs.screenHeight)

                val mappedEndX = if (obj.has("endX") && obj.has("endY")) {
                    CoordinateNormalizer.mapPoint(Point2D(obj.optDouble("endX").toFloat(), obj.optDouble("endY").toFloat()), transform, curSpecs.screenWidth, curSpecs.screenHeight).x
                } else null
                val mappedEndY = if (obj.has("endX") && obj.has("endY")) {
                    CoordinateNormalizer.mapPoint(Point2D(obj.optDouble("endX").toFloat(), obj.optDouble("endY").toFloat()), transform, curSpecs.screenWidth, curSpecs.screenHeight).y
                } else null

                val mList = ArrayList<Int>()
                if (obj.has("multiTemplateIndices")) {
                    val mArr = obj.getJSONArray("multiTemplateIndices")
                    for (k in 0 until mArr.length()) mList.add(mArr.getInt(k))
                }

                val mPathsList = ArrayList<String>()
                if (obj.has("multiTemplatePaths")) {
                    val pArr = obj.getJSONArray("multiTemplatePaths")
                    for (k in 0 until pArr.length()) {
                        mPathsList.add(resolveTemplatePath(pArr.getString(k)))
                    }
                }

                val pList = ArrayList<Point2D>()
                if (obj.has("pathPoints")) {
                    val pArr = obj.getJSONArray("pathPoints")
                    for (k in 0 until pArr.length()) {
                        val ptObj = pArr.getJSONObject(k)
                        pList.add(CoordinateNormalizer.mapPoint(Point2D(ptObj.getDouble("x").toFloat(), ptObj.getDouble("y").toFloat()), transform, curSpecs.screenWidth, curSpecs.screenHeight))
                    }
                }

                val aList = ArrayList<Point2D>()
                if (obj.has("primaryAnchorPoints")) {
                    val aArr = obj.getJSONArray("primaryAnchorPoints")
                    for (k in 0 until aArr.length()) {
                        val ptObj = aArr.getJSONObject(k)
                        aList.add(CoordinateNormalizer.mapPoint(Point2D(ptObj.getDouble("x").toFloat(), ptObj.getDouble("y").toFloat()), transform, curSpecs.screenWidth, curSpecs.screenHeight))
                    }
                }

                val rawTPath = obj.optString("templatePath", "")
                val resolvedTPath = resolveTemplatePath(rawTPath)
                val loadedSim = obj.optInt("similarityPercent", 80)
                val subTarget = obj.optString("subroutineTarget", obj.optString("targetScriptToLoad", obj.optString("subroutineTag", "")))

                loadedActions.add(
                    MacroAction(
                        id = obj.optInt("id", loadedActions.size + 1),
                        type = type,
                        posX = mappedPos.x,
                        posY = mappedPos.y,
                        endX = mappedEndX,
                        endY = mappedEndY,
                        delayMs = obj.optLong("delay", 1000L),
                        holdDurationMs = obj.optLong("holdDuration", 120L),
                        checkIntervalMs = obj.optLong("checkInterval", 300L),
                        repeatCount = obj.optInt("repeatCount", 1),
                        randomRadiusPx = obj.optInt("randomRadius", 0),
                        pinchStartDistance = obj.optDouble("pinchStartDistance", 300.0).toFloat(),
                        pinchEndDistance = obj.optDouble("pinchEndDistance", 600.0).toFloat(),
                        selectedTemplateIndex = obj.optInt("selectedTemplateIndex", -1),
                        multiTemplateIndices = mList,
                        multiTemplatePaths = mPathsList,
                        templatePath = resolvedTPath,
                        similarityPercent = loadedSim,
                        aiTimeoutSeconds = obj.optInt("aiTimeoutSeconds", 5),
                        clickAiTarget = obj.optBoolean("clickAiTarget", true),
                        isWaitUntilMode = obj.optBoolean("isWaitUntilMode", false),
                        scanStepPreset = obj.optInt("scanStepPreset", 2),
                        isContourMode = obj.optBoolean("isContourMode", true),
                        isFastMode = obj.optBoolean("isFastMode", true),
                        isMultiScaleMode = obj.optBoolean("isMultiScaleMode", false),
                        colorDeltaEMode = obj.optBoolean("colorDeltaEMode", false),
                        isShapeOnlyMode = obj.optBoolean("isShapeOnlyMode", false),
                        shapeExpansion = obj.optInt("shapeExpansion", 45),
                        paddingOffsetPx = obj.optInt("paddingOffsetPx", 2),
                        isCircleShape = obj.optBoolean("isCircleShape", false),
                        useCustomClickOffset = obj.optBoolean("useCustomClickOffset", false),
                        clickOffsetX = obj.optDouble("clickOffsetX", 0.0).toFloat(),
                        clickOffsetY = obj.optDouble("clickOffsetY", 0.0).toFloat(),
                        primaryAnchorPoints = aList,
                        jumpToStepOnMatch = if (obj.has("jumpToStepOnMatch") && obj.getInt("jumpToStepOnMatch") > 0) obj.getInt("jumpToStepOnMatch") else null,
                        jumpToStepOnTimeout = if (obj.has("jumpToStepOnTimeout") && obj.getInt("jumpToStepOnTimeout") > 0) obj.getInt("jumpToStepOnTimeout") else null,
                        pathPoints = pList,
                        targetScriptOrQuery = obj.optString("targetScriptToLoad", ""),
                        subroutineTag = obj.optString("subroutineTag", ""),
                        subroutineTarget = subTarget,

                        targetColorHex = obj.optString("targetColorHex", "#00F5D4"),
                        colorTolerance = obj.optInt("colorTolerance", 15),
                        notifyOnMatch = obj.optBoolean("notifyOnMatch", false),
                        roiLeft = if (obj.has("roiLeft")) obj.getInt("roiLeft") else null,
                        roiTop = if (obj.has("roiTop")) obj.getInt("roiTop") else null,
                        roiRight = if (obj.has("roiRight")) obj.getInt("roiRight") else null,
                        roiBottom = if (obj.has("roiBottom")) obj.getInt("roiBottom") else null
                    )
                )
            }

            MacroScenario(
                name = safeName,
                version = version,
                deviceSpecs = curSpecs,
                actions = loadedActions,
                globalClickDurationMs = globalClick,
                globalSwipeDurationMs = globalSwipe
            )
        } catch (e: Exception) {
            AppLogger.logError(context, "STORAGE", e)
            null
        }
    }

    override fun saveGraphScenario(scenario: GraphMacroScenario): Boolean {
        return try {
            val safeName = normalizeScenarioName(scenario.name)
            val root = JSONObject()

            root.put("id", scenario.id)
            root.put("name", scenario.name)
            root.put("version", scenario.version)
            root.put("entryNodeId", scenario.entryNodeId)
            root.put("globalClickDurationMs", scenario.globalClickDurationMs)
            root.put("globalSwipeDurationMs", scenario.globalSwipeDurationMs)
            root.put("createdAt", scenario.createdAt)
            root.put("modifiedAt", System.currentTimeMillis())

            val devSpecs = JSONObject().apply {
                put("screenWidth", scenario.deviceSpecs.screenWidth)
                put("screenHeight", scenario.deviceSpecs.screenHeight)
                put("densityDpi", scenario.deviceSpecs.densityDpi)
                put("density", scenario.deviceSpecs.density.toDouble())
                put("isLandscape", scenario.deviceSpecs.isLandscape)
            }
            root.put("deviceSpecs", devSpecs)

            val nodesObj = JSONObject()
            for ((nodeId, node) in scenario.nodes) {
                val nodeItem = JSONObject().apply {
                    put("id", node.id)
                    put("title", node.title)
                    put("canvasX", node.canvasX.toDouble())
                    put("canvasY", node.canvasY.toDouble())
                    put("evaluationPolicy", node.evaluationPolicy.name)
                    put("timeoutSeconds", node.timeoutSeconds)
                    put("isInfiniteWait", node.isInfiniteWait)
                    put("pollIntervalMs", node.pollIntervalMs)

                    val stdPortsArr = JSONArray()
                    node.standardPorts.forEach { stdPortsArr.put(it) }
                    put("standardPorts", stdPortsArr)

                    val triggersArr = JSONArray()
                    for (trig in node.triggers) {
                        triggersArr.put(JSONObject().apply {
                            put("id", trig.id)
                            put("name", trig.name)
                            put("type", trig.type.name)
                            put("templatePath", trig.templatePath)
                            val multiPathsArr = JSONArray()
                            trig.multiTemplatePaths.forEach { multiPathsArr.put(it) }
                            put("multiTemplatePaths", multiPathsArr)
                            put("similarityThreshold", trig.similarityThreshold)
                            put("targetQueryOrColor", trig.targetQueryOrColor)
                            put("colorTolerance", trig.colorTolerance)
                            put("isContourMode", trig.isContourMode)
                            put("isShapeOnlyMode", trig.isShapeOnlyMode)
                            put("isMultiScaleMode", trig.isMultiScaleMode)
                            put("colorDeltaEMode", trig.colorDeltaEMode)
                            put("shapeExpansion", trig.shapeExpansion)
                            put("paddingOffsetPx", trig.paddingOffsetPx)
                            put("isCircleShape", trig.isCircleShape)
                            trig.roiLeft?.let { put("roiLeft", it) }
                            trig.roiTop?.let { put("roiTop", it) }
                            trig.roiRight?.let { put("roiRight", it) }
                            trig.roiBottom?.let { put("roiBottom", it) }
                            put("autoClickTarget", trig.autoClickTarget)
                            put("useCustomClick", trig.useCustomClick)
                            put("clickOffsetX", trig.clickOffset.x.toDouble())
                            put("clickOffsetY", trig.clickOffset.y.toDouble())
                            put("targetPortId", trig.targetPortId)
                        })
                    }
                    put("triggers", triggersArr)

                    val actionsArr = JSONArray()
                    for (act in node.entryActions) {
                        actionsArr.put(JSONObject().apply {
                            put("id", act.id)
                            put("type", act.type.name)
                            put("x", act.x.toDouble())
                            put("y", act.y.toDouble())
                            act.endX?.let { put("endX", it.toDouble()) }
                            act.endY?.let { put("endY", it.toDouble()) }
                            put("holdDurationMs", act.holdDurationMs)
                            put("delayAfterMs", act.delayAfterMs)
                            put("repeatCount", act.repeatCount)
                            put("randomRadiusPx", act.randomRadiusPx)
                            put("pinchStartDistance", act.pinchStartDistance.toDouble())
                            put("pinchEndDistance", act.pinchEndDistance.toDouble())
                            put("subroutineTarget", act.subroutineTarget)
                            if (act.pathPoints.isNotEmpty()) {
                                val ptsArr = JSONArray()
                                for (pt in act.pathPoints) {
                                    ptsArr.put(JSONObject().apply {
                                        put("x", pt.x.toDouble())
                                        put("y", pt.y.toDouble())
                                    })
                                }
                                put("pathPoints", ptsArr)
                            }
                        })
                    }
                    put("entryActions", actionsArr)
                }
                nodesObj.put(nodeId, nodeItem)
            }
            root.put("nodes", nodesObj)

            val edgesArr = JSONArray()
            for (edge in scenario.edges) {
                edgesArr.put(JSONObject().apply {
                    put("id", edge.id)
                    put("fromNodeId", edge.fromNodeId)
                    put("fromPortId", edge.fromPortId)
                    put("toNodeId", edge.toNodeId)
                    put("toPortId", edge.toPortId)
                })
            }
            root.put("edges", edgesArr)

            val targetFile = File(scriptsDir, "$safeName.graph.json")
            FileOutputStream(targetFile).use { it.write(root.toString().toByteArray()) }
            AppLogger.log(context, "STORAGE", "Графовый сценарий '$safeName' успешно сохранен (${scenario.nodes.size} нод, ${scenario.edges.size} связей)")
            true
        } catch (e: Exception) {
            AppLogger.logError(context, "STORAGE", e)
            false
        }
    }

    override fun loadGraphScenario(name: String): GraphMacroScenario? {
        return try {
            val safeName = normalizeScenarioName(name)
            val file = File(scriptsDir, "$safeName.graph.json")
            if (!file.exists()) return null

            val root = JSONObject(file.readText())
            val dm = context.resources.displayMetrics
            val curSpecs = DeviceDisplaySpecs(
                screenWidth = dm.widthPixels, screenHeight = dm.heightPixels,
                densityDpi = dm.densityDpi, density = dm.density, isLandscape = dm.widthPixels > dm.heightPixels
            )

            val devSpecsObj = root.optJSONObject("deviceSpecs")
            val srcSpecs = if (devSpecsObj != null) {
                val sW = devSpecsObj.optInt("screenWidth", curSpecs.screenWidth)
                val sH = devSpecsObj.optInt("screenHeight", curSpecs.screenHeight)
                val sDpi = devSpecsObj.optInt("densityDpi", curSpecs.densityDpi)
                val sDensity = devSpecsObj.optDouble("density", curSpecs.density.toDouble()).toFloat()
                val sLand = devSpecsObj.optBoolean("isLandscape", sW > sH)
                DeviceDisplaySpecs(sW, sH, sDpi, sDensity, sLand)
            } else curSpecs

            val transform = CoordinateNormalizer.calculateTransform(srcSpecs, curSpecs)
            val nodesMap = LinkedHashMap<String, ScenarioNode>()

            if (root.has("nodes")) {
                val nodesElement = root.get("nodes")
                if (nodesElement is JSONObject) {
                    val keys = nodesElement.keys()
                    while (keys.hasNext()) {
                        val nid = keys.next()
                        val nObj = nodesElement.getJSONObject(nid)
                        nodesMap[nid] = parseScenarioNode(nObj, transform, curSpecs)
                    }
                } else if (nodesElement is JSONArray) {
                    for (i in 0 until nodesElement.length()) {
                        val nObj = nodesElement.getJSONObject(i)
                        val nid = nObj.getString("id")
                        nodesMap[nid] = parseScenarioNode(nObj, transform, curSpecs)
                    }
                }
            }

            val edgesList = mutableListOf<ScenarioEdge>()
            val edgesArr = root.optJSONArray("edges") ?: JSONArray()
            for (i in 0 until edgesArr.length()) {
                val eObj = edgesArr.getJSONObject(i)
                edgesList.add(
                    ScenarioEdge(
                        id = eObj.getString("id"),
                        fromNodeId = eObj.getString("fromNodeId"),
                        fromPortId = eObj.getString("fromPortId"),
                        toNodeId = eObj.getString("toNodeId"),
                        toPortId = eObj.optString("toPortId", "in_entry")
                    )
                )
            }

            GraphMacroScenario(
                id = root.optString("id", UUID.randomUUID().toString()),
                name = safeName,
                version = root.optInt("version", 2),
                deviceSpecs = curSpecs,
                entryNodeId = root.optString("entryNodeId", nodesMap.keys.firstOrNull() ?: ""),
                nodes = nodesMap,
                edges = edgesList,
                globalClickDurationMs = root.optLong("globalClickDurationMs", 120L),
                globalSwipeDurationMs = root.optLong("globalSwipeDurationMs", 300L),
                createdAt = root.optLong("createdAt", System.currentTimeMillis()),
                modifiedAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            AppLogger.logError(context, "STORAGE", e)
            null
        }
    }

    private fun parseScenarioNode(
        nObj: JSONObject,
        transform: CoordinateNormalizer.ScaleTransform,
        curSpecs: DeviceDisplaySpecs
    ): ScenarioNode {
        val nid = nObj.getString("id")
        val title = nObj.optString("title", "Фаза $nid")
        val canvasX = nObj.optDouble("canvasX", 100.0).toFloat()
        val canvasY = nObj.optDouble("canvasY", 100.0).toFloat()
        val policyStr = nObj.optString("evaluationPolicy", EvaluationPolicy.FIRST_MATCH_WINS.name)
        val policy = try { EvaluationPolicy.valueOf(policyStr) } catch (_: Exception) { EvaluationPolicy.FIRST_MATCH_WINS }
        val timeoutSec = nObj.optInt("timeoutSeconds", 5)
        val isInf = nObj.optBoolean("isInfiniteWait", false)
        val pollMs = nObj.optLong("pollIntervalMs", 250L)

        val stdPortsList = mutableListOf<String>()
        val stdArr = nObj.optJSONArray("standardPorts")
        if (stdArr != null) {
            for (k in 0 until stdArr.length()) stdPortsList.add(stdArr.getString(k))
        }
        if (stdPortsList.isEmpty()) stdPortsList.addAll(listOf("out_default", "out_timeout"))

        val triggersList = mutableListOf<NodeTriggerSpec>()
        val trigArr = nObj.optJSONArray("triggers")
        if (trigArr != null) {
            for (k in 0 until trigArr.length()) {
                val tObj = trigArr.getJSONObject(k)
                val tType = try { ActionType.valueOf(tObj.optString("type", "TRIGGER")) } catch (_: Exception) { ActionType.TRIGGER }
                val multiPaths = mutableListOf<String>()
                val mpArr = tObj.optJSONArray("multiTemplatePaths")
                if (mpArr != null) {
                    for (p in 0 until mpArr.length()) multiPaths.add(resolveTemplatePath(mpArr.getString(p)))
                }

                triggersList.add(
                    NodeTriggerSpec(
                        id = tObj.getString("id"),
                        name = tObj.optString("name", "Триггер"),
                        type = tType,
                        templatePath = resolveTemplatePath(tObj.optString("templatePath", "")),
                        multiTemplatePaths = multiPaths,
                        similarityThreshold = tObj.optInt("similarityThreshold", 80),
                        targetQueryOrColor = tObj.optString("targetQueryOrColor", ""),
                        colorTolerance = tObj.optInt("colorTolerance", 15),
                        isContourMode = tObj.optBoolean("isContourMode", true),
                        isShapeOnlyMode = tObj.optBoolean("isShapeOnlyMode", false),
                        isMultiScaleMode = tObj.optBoolean("isMultiScaleMode", false),
                        colorDeltaEMode = tObj.optBoolean("colorDeltaEMode", false),
                        shapeExpansion = tObj.optInt("shapeExpansion", 45),
                        paddingOffsetPx = tObj.optInt("paddingOffsetPx", 2),
                        isCircleShape = tObj.optBoolean("isCircleShape", false),
                        roiLeft = if (tObj.has("roiLeft")) tObj.getInt("roiLeft") else null,
                        roiTop = if (tObj.has("roiTop")) tObj.getInt("roiTop") else null,
                        roiRight = if (tObj.has("roiRight")) tObj.getInt("roiRight") else null,
                        roiBottom = if (tObj.has("roiBottom")) tObj.getInt("roiBottom") else null,
                        autoClickTarget = tObj.optBoolean("autoClickTarget", true),
                        useCustomClick = tObj.optBoolean("useCustomClick", false),
                        clickOffset = Point2D(tObj.optDouble("clickOffsetX", 0.0).toFloat(), tObj.optDouble("clickOffsetY", 0.0).toFloat()),
                        targetPortId = tObj.optString("targetPortId", "out_match_${tObj.getString("id")}")
                    )
                )
            }
        }

        val actionsList = mutableListOf<NodeActionSpec>()
        val actArr = nObj.optJSONArray("entryActions")
        if (actArr != null) {
            for (k in 0 until actArr.length()) {
                val aObj = actArr.getJSONObject(k)
                val aType = try { ActionType.valueOf(aObj.optString("type", "CLICK")) } catch (_: Exception) { ActionType.CLICK }
                val origX = aObj.optDouble("x", 0.0).toFloat()
                val origY = aObj.optDouble("y", 0.0).toFloat()
                val mapped = CoordinateNormalizer.mapPoint(Point2D(origX, origY), transform, curSpecs.screenWidth, curSpecs.screenHeight)

                val mappedEnd = if (aObj.has("endX") && aObj.has("endY")) {
                    CoordinateNormalizer.mapPoint(Point2D(aObj.optDouble("endX").toFloat(), aObj.optDouble("endY").toFloat()), transform, curSpecs.screenWidth, curSpecs.screenHeight)
                } else null

                val pathPts = mutableListOf<Point2D>()
                val ptsArr = aObj.optJSONArray("pathPoints")
                if (ptsArr != null) {
                    for (p in 0 until ptsArr.length()) {
                        val ptObj = ptsArr.getJSONObject(p)
                        pathPts.add(CoordinateNormalizer.mapPoint(Point2D(ptObj.getDouble("x").toFloat(), ptObj.getDouble("y").toFloat()), transform, curSpecs.screenWidth, curSpecs.screenHeight))
                    }
                }

                actionsList.add(
                    NodeActionSpec(
                        id = aObj.getString("id"),
                        type = aType,
                        x = mapped.x,
                        y = mapped.y,
                        endX = mappedEnd?.x,
                        endY = mappedEnd?.y,
                        holdDurationMs = aObj.optLong("holdDurationMs", 120L),
                        delayAfterMs = aObj.optLong("delayAfterMs", 200L),
                        repeatCount = aObj.optInt("repeatCount", 1),
                        randomRadiusPx = aObj.optInt("randomRadiusPx", 0),
                        pinchStartDistance = aObj.optDouble("pinchStartDistance", 300.0).toFloat(),
                        pinchEndDistance = aObj.optDouble("pinchEndDistance", 600.0).toFloat(),
                        pathPoints = pathPts,
                        subroutineTarget = aObj.optString("subroutineTarget", "")
                    )
                )
            }
        }

        return ScenarioNode(
            id = nid,
            title = title,
            canvasX = canvasX,
            canvasY = canvasY,
            evaluationPolicy = policy,
            triggers = triggersList,
            timeoutSeconds = timeoutSec,
            isInfiniteWait = isInf,
            pollIntervalMs = pollMs,
            entryActions = actionsList,
            standardPorts = stdPortsList
        )
    }

    override fun hasGraphScenario(name: String): Boolean {
        val safe = normalizeScenarioName(name)
        return File(scriptsDir, "$safe.graph.json").exists()
    }

    override fun listScenarios(): List<String> {
        return scriptsDir.listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".json")) }
            ?.map {
                if (it.name.endsWith(".graph.json")) it.name.removeSuffix(".graph.json") else it.nameWithoutExtension
            }
            ?.distinct()
            ?.sorted() ?: emptyList()
    }

    override fun deleteScenario(name: String): Boolean {
        val safe = normalizeScenarioName(name)
        val file = File(scriptsDir, "$safe.json")
        val graphFile = File(scriptsDir, "$safe.graph.json")
        val deletedLinear = if (file.exists()) file.delete() else false
        val deletedGraph = if (graphFile.exists()) graphFile.delete() else false
        return deletedLinear || deletedGraph
    }

    override fun duplicateScenario(name: String): Boolean {
        val originalLinear = loadScenario(name)
        val originalGraph = loadGraphScenario(name)
        var success = false
        if (originalLinear != null) {
            success = saveScenario(originalLinear.copy(name = "${originalLinear.name}_копия"))
        }
        if (originalGraph != null) {
            success = saveGraphScenario(originalGraph.copy(name = "${originalGraph.name}_копия", id = UUID.randomUUID().toString())) || success
        }
        return success
    }
}
