package com.hikemvp.group

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import org.osmdroid.api.IGeoPoint
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import java.lang.ref.WeakReference
import kotlin.math.*

/**
 * GroupOverlay — rendu avec labels, clustering, bulle + sélection.
 * Ajouts : sélection d'un membre (visuel), méthodes setSelected(id) / getSelected().
 * API GroupOverlayApi intacte.
 */
class GroupOverlay() : Overlay(), GroupOverlayApi {

    constructor(ctx: Context) : this() { ctxRef = WeakReference(ctx) }
    constructor(ctx: Context, map: MapView) : this() { ctxRef = WeakReference(ctx); attachTo(map) }

    // Refs
    private var ctxRef: WeakReference<Context?>? = null
    private var mapRef: WeakReference<MapView?>? = null

    // Options
    private var autoCenterOnTap: Boolean = true
    private var autoCenterZoom: Double? = null
    private var clustering: Boolean = false
    private var labelsEnabled: Boolean = false
    private var dotRadiusDp: Float = 8f

    // Data
    private val positions: LinkedHashMap<String, GeoPoint> = LinkedHashMap()
    private val membersData: LinkedHashMap<String, GroupMember> = LinkedHashMap()
    override val members: MutableMap<String, GeoPoint> get() = positions

    // UI state
    private var bubbleId: String? = null
    private var bubbleUntilMs: Long = 0L
    private var selectedId: String? = null

    // Paints
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.WHITE }
    private val selRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f; color = Color.YELLOW }
    private val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 11f * resourcesDensity(); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val labelBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 0, 0, 0); style = Paint.Style.FILL }
    private val bubbleBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(220, 30,30,30); style = Paint.Style.FILL }
    private val bubbleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f }
    private val labelPad = 4f

    // API ajout (spécifique GroupOverlay)
    fun setLabelsEnabled(enabled: Boolean) { labelsEnabled = enabled; mapRef?.get()?.invalidate() }
    fun setDotRadiusDp(dp: Float) { dotRadiusDp = dp.coerceIn(4f, 18f); mapRef?.get()?.invalidate() }
    fun setSelected(id: String?) { selectedId = id; mapRef?.get()?.invalidate() }
    fun getSelected(): String? = selectedId

    // ===== Connexion carte =====
    override fun attachTo(mapView: MapView) {
        mapRef = WeakReference(mapView)
        if (ctxRef?.get() == null) ctxRef = WeakReference(mapView.context.applicationContext)
    }
    override fun detachFrom(mapView: MapView) { val m = mapRef?.get(); if (m === mapView) mapRef = null }

    // ===== Mises à jour =====
    override fun setMembers(list: List<GroupMember>, map: MapView) {
        positions.clear(); membersData.clear()
        list.forEach { m -> positions[m.id] = m.point; membersData[m.id] = m }
        map.invalidate()
    }
    override fun addOrUpdateMember(member: GroupMember, map: MapView) {
        positions[member.id] = member.point; membersData[member.id] = member; map.invalidate()
    }
    override fun removeMemberById(id: String, map: MapView) { positions.remove(id); membersData.remove(id); if (selectedId == id) selectedId = null; map.invalidate() }
    override fun upsertPosition(id: String, gp: GeoPoint) {
        positions[id] = gp; membersData[id]?.let { it.point = gp } ?: run { membersData[id] = GroupMember(id, id, gp) }
        mapRef?.get()?.invalidate()
    }

    // ===== Focus & Zoom =====
    override fun focusOn(id: String, map: MapView) { positions[id]?.let { runCatching { map.controller.setCenter(it) } } }
    override fun focusSmoothOn(id: String, map: MapView, zoomLevel: Double?) {
        val gp = positions[id] ?: return
        runCatching { if (zoomLevel != null) map.controller.animateTo(gp, zoomLevel, 600L) else map.controller.animateTo(gp) }
    }
    override fun zoomToAll() {
        val mv = mapRef?.get() ?: com.hikemvp.GroupGlobals.mapView ?: return
        if (positions.isEmpty()) return
        runCatching {
            val bbox = BoundingBox.fromGeoPointsSafe(positions.values.toList())
            mv.zoomToBoundingBox(bbox, true)
        }.onFailure {
            positions.values.lastOrNull()?.let { gp -> runCatching { mv.controller.setCenter(gp) } }
        }
    }

    override fun setAutoCenterOnTap(enabled: Boolean, zoomLevel: Double?) { autoCenterOnTap = enabled; autoCenterZoom = zoomLevel }
    override fun setClustering(enabled: Boolean, map: MapView) { clustering = enabled; map.invalidate() }
    override fun setMembers(any: Any?, replace: Boolean) {
        if (replace) { positions.clear(); membersData.clear() }
        when (any) {
            is List<*> -> any.forEach { item ->
                when (item) {
                    is GroupMember -> { positions[item.id] = item.point; membersData[item.id] = item }
                    is Pair<*,*> -> {
                        val id = item.first?.toString() ?: return@forEach
                        val gp = item.second as? GeoPoint ?: return@forEach
                        positions[id] = gp; membersData[id] = GroupMember(id, id, gp)
                    }
                }
            }
        }
        mapRef?.get()?.invalidate()
    }

    // ===== Dessin =====
    override fun draw(c: Canvas?, osmv: MapView?, shadow: Boolean) {
        if (shadow || c == null || osmv == null) return
        if (positions.isEmpty()) return
        val proj = osmv.projection
        val r = dotRadiusDp * resourcesDensity()
        val zoom = osmv.zoomLevelDouble

        if (clustering && zoom < 14.0) {
            drawClusters(c, proj, r)
        } else {
            membersData.values.forEach { m -> drawMember(c, proj, m, r, zoom, selectedId == m.id) }
        }

        if (bubbleId != null && System.currentTimeMillis() <= bubbleUntilMs) {
            membersData[bubbleId!!]?.let { drawBubble(c, proj, it) }
        }
    }

    private fun drawClusters(c: Canvas, proj: Projection, r: Float) {
        val cellPx = (64f * resourcesDensity()).toInt().coerceAtLeast(24)
        val buckets = HashMap<Long, MutableList<GroupMember>>()
        fun key(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)

        membersData.values.forEach { m ->
            val p = Point()
            proj.toPixels(m.point as IGeoPoint, p)
            val gx = floor(p.x / cellPx.toDouble()).toInt()
            val gy = floor(p.y / cellPx.toDouble()).toInt()
            val k = key(gx, gy)
            buckets.getOrPut(k) { ArrayList() }.add(m)
        }

        buckets.forEach { (_, list) ->
            var sx = 0f; var sy = 0f
            val tmp = Point()
            list.forEach { m -> proj.toPixels(m.point as IGeoPoint, tmp); sx += tmp.x; sy += tmp.y }
            val cx = sx / list.size; val cy = sy / list.size
            val count = list.size
            val radius = r + (min(18, 4 + count)).toFloat()
            val centerColor = Color.argb(220, 60, 60, 60)
            dotPaint.color = centerColor
            c.drawCircle(cx, cy, radius, dotPaint)
            c.drawCircle(cx, cy, radius, ringPaint)
            txtPaint.color = Color.WHITE
            c.drawText(count.toString(), cx, cy + (txtPaint.textSize * 0.35f), txtPaint)
        }
    }

    private fun drawMember(c: Canvas, proj: Projection, m: GroupMember, r: Float, zoom: Double, selected: Boolean) {
        val pt = Point()
        proj.toPixels(m.point as IGeoPoint, pt)
        dotPaint.color = m.color
        c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), r, dotPaint)
        c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), r, ringPaint)

        if (selected) {
            c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), r + 5f, selRingPaint)
        }

        val initial = (m.name.firstOrNull()?.uppercaseChar() ?: ' ').toString()
        val lum = Color.red(m.color) * 0.299 + Color.green(m.color) * 0.587 + Color.blue(m.color) * 0.114
        txtPaint.color = if (lum < 140) Color.WHITE else Color.BLACK
        c.drawText(initial, pt.x.toFloat(), pt.y + (txtPaint.textSize * 0.35f), txtPaint)

        if (labelsEnabled || zoom >= 15.0) {
            val label = m.name.ifBlank { m.id }
            val pad = labelPad * resourcesDensity()
            val textW = txtPaint.measureText(label)
            val textH = txtPaint.fontMetrics.run { bottom - top }
            val left = pt.x - textW/2f - pad
            val top = pt.y + r + 6f
            val right = pt.x + textW/2f + pad
            val bottom = top + textH + pad*2f
            c.drawRoundRect(RectF(left, top, right, bottom), 8f, 8f, labelBg)
            c.drawText(label, pt.x.toFloat(), top + pad - txtPaint.fontMetrics.top, txtPaint)
        }
    }

    private fun drawBubble(c: Canvas, proj: Projection, m: GroupMember) {
        val pt = Point()
        proj.toPixels(m.point as IGeoPoint, pt)
        val text = "${m.name.ifBlank { m.id }}\n${"%.5f".format(m.point.latitude)}, ${"%.5f".format(m.point.longitude)}"
        val lines = text.split("\n")
        val pad = 8f * resourcesDensity()
        val w = lines.maxOf { txtPaint.measureText(it) } + pad * 2
        val h = (txtPaint.fontMetrics.run { bottom - top } * lines.size) + pad * 2
        val rect = RectF(pt.x + 12f, pt.y - h - 12f, pt.x + 12f + w, pt.y - 12f)
        c.drawRoundRect(rect, 10f, 10f, bubbleBg)
        c.drawRoundRect(rect, 10f, 10f, bubbleStroke)

        var ty = rect.top + pad - txtPaint.fontMetrics.top
        lines.forEach { l ->
            c.drawText(l, rect.left + pad, ty, txtPaint.apply { textAlign = Paint.Align.LEFT; color = Color.WHITE })
            ty += txtPaint.fontMetrics.run { bottom - top }
        }
        val path = Path()
        path.moveTo(pt.x.toFloat(), pt.y.toFloat())
        path.lineTo(rect.left + 14f, rect.bottom)
        path.lineTo(rect.left + 28f, rect.bottom)
        path.close()
        c.drawPath(path, bubbleBg)
        c.drawPath(path, bubbleStroke)
    }

    // ===== Gestes =====
    override fun onSingleTapConfirmed(e: MotionEvent?, mapView: MapView?): Boolean {
        if (e == null || mapView == null) return false
        val proj = mapView.projection
        val touch = Point(e.x.roundToInt(), e.y.roundToInt())
        val rPx = (dotRadiusDp * resourcesDensity()).coerceAtLeast(8f) * 1.8f

        if (clustering && mapView.zoomLevelDouble < 14.0) {
            val cellPx = (64f * resourcesDensity()).toInt().coerceAtLeast(24)
            val gx = floor(touch.x / cellPx.toDouble()).toInt()
            val gy = floor(touch.y / cellPx.toDouble()).toInt()
            val list = ArrayList<GeoPoint>()
            val tmp = Point()
            membersData.values.forEach { m ->
                proj.toPixels(m.point as IGeoPoint, tmp)
                val cx = floor(tmp.x / cellPx.toDouble()).toInt()
                val cy = floor(tmp.y / cellPx.toDouble()).toInt()
                if (cx == gx && cy == gy) list.add(m.point)
            }
            if (list.isNotEmpty()) {
                val bb = BoundingBox.fromGeoPointsSafe(list)
                mapView.zoomToBoundingBox(bb, true)
                return true
            }
        }

        // sélection + recentrage doux si activé
        var bestId: String? = null
        var bestDist = Float.MAX_VALUE
        val p = Point()
        positions.forEach { (id, gp) ->
            proj.toPixels(gp as IGeoPoint, p)
            val d = hypot((p.x - touch.x).toFloat(), (p.y - touch.y).toFloat())
            if (d < bestDist) { bestDist = d; bestId = id }
        }
        if (bestId != null && bestDist <= rPx * 2.5f) {
            selectedId = bestId
            if (autoCenterOnTap) focusSmoothOn(bestId!!, mapView, autoCenterZoom)
            mapView.postInvalidate()
            return true
        }
        return false
    }

    override fun onLongPress(e: MotionEvent?, mapView: MapView?): Boolean {
        if (e == null || mapView == null) return false
        val proj = mapView.projection
        val touch = Point(e.x.roundToInt(), e.y.roundToInt())
        val p = Point()
        var bestId: String? = null
        var bestDist = Float.MAX_VALUE
        positions.forEach { (id, gp) ->
            proj.toPixels(gp as IGeoPoint, p)
            val d = hypot((p.x - touch.x).toFloat(), (p.y - touch.y).toFloat())
            if (d < bestDist) { bestDist = d; bestId = id }
        }
        return if (bestId != null && bestDist <= 96f * resourcesDensity()) {
            bubbleId = bestId
            selectedId = bestId
            bubbleUntilMs = System.currentTimeMillis() + 2500L
            mapView.postInvalidate()
            true
        } else false
    }

    private fun resourcesDensity(): Float {
        val ctx = ctxRef?.get() ?: return 1f
        return ctx.resources.displayMetrics.density.coerceAtLeast(1f)
    }
}
