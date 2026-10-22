package com.cuboland.app

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import java.text.Normalizer

/**
 * Inspetor de blocos: lista pesquisável, miniatura em cubo, texturas das 3 faces,
 * diagnóstico dos índices do atlas e acesso a uma sala de teste no mundo.
 */
class BlockInspector(
    private val act: Activity,
    menu: List<Pair<World.GalCat, List<World.GalSec>>>,
    private val onBack: () -> Unit,
    private val onClose: () -> Unit,
    private val onTest: (Int) -> Unit
) {
    val view = FrameLayout(act)
    private data class Entry(val id: Int, val name: String, val category: String)
    private inner class Holder(val cube: CubePreviewView, val name: TextView, val meta: TextView)

    private val entries: List<Entry> = menu.flatMap { pair ->
        pair.first.ids.mapNotNull { id ->
            val name = B.names.getOrNull(id).orEmpty()
            if (id > 0 && name.isNotBlank()) Entry(id, name, pair.first.name) else null
        }
    }.distinctBy { it.id }.sortedBy { normalize(it.name) }

    private var filtered: List<Entry> = entries
    private var selectedId: Int = entries.firstOrNull()?.id ?: -1
    private var searchText = ""
    private var categoryPosition = 0

    private val search = EditText(act)
    private val category = Spinner(act)
    private val count = TextView(act)
    private val list = ListView(act)
    private val grid = GridView(act)
    private var scanMode = true
    private var onlyBad = false
    private lateinit var gridAdapter: BaseAdapter
    private val modeButton = Button(act)
    private val issueButton = Button(act)
    private val nameLine = TextView(act)
    private val detailsLine = TextView(act)
    private val statusLine = TextView(act)
    private val cube = CubePreviewView(act)
    private val faces = Array(3) { TextureTileView(act) }
    private val faceIds = Array(3) { TextView(act) }
    private lateinit var adapter: BaseAdapter
    private val filters = listOf("Todas") + menu.map { it.first.name }

    init {
        view.setBackgroundColor(Color.rgb(12, 16, 23))
        val column = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(act.dp(10), act.dp(8), act.dp(10), act.dp(8))
        }
        view.addView(column, FrameLayout.LayoutParams(-1, -1))

        val header = LinearLayout(act).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        val back = button("← Voltar", Color.rgb(76, 86, 102)) { onBack() }
        val title = TextView(act).apply {
            text = "INSPETOR DE BLOCOS"
            setTextColor(Color.rgb(255, 214, 120)); textSize = 17f
            gravity = Gravity.CENTER; typeface = act.mcFont()
        }
        val close = button("Fechar ✕", Color.rgb(174, 63, 65)) { onClose() }
        header.addView(back, LinearLayout.LayoutParams(act.dp(84), act.dp(42)))
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(close, LinearLayout.LayoutParams(act.dp(84), act.dp(42)))
        column.addView(header)

        val hint = TextView(act).apply {
            text = "Pesquise um bloco, confira as faces e abra um teste isolado no mundo."
            setTextColor(Color.rgb(180, 191, 207)); textSize = 12f
            setPadding(act.dp(2), act.dp(5), act.dp(2), act.dp(7))
        }
        column.addView(hint)

        val filterRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        search.hint = "Buscar nome ou ID..."
        search.setSingleLine(true)
        search.textSize = 14f
        search.setTextColor(Color.WHITE)
        search.setHintTextColor(Color.rgb(135, 146, 162))
        search.setPadding(act.dp(10), 0, act.dp(8), 0)
        search.background = rounded(Color.rgb(31, 38, 50), Color.rgb(76, 91, 112), 10)
        filterRow.addView(search, LinearLayout.LayoutParams(0, act.dp(46), 1f).apply { rightMargin = act.dp(6) })

        val spinnerAdapter = ArrayAdapter(act, android.R.layout.simple_spinner_item, filters).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        category.adapter = spinnerAdapter
        category.setBackgroundColor(Color.rgb(35, 43, 56))
        filterRow.addView(category, LinearLayout.LayoutParams(act.dp(120), act.dp(46)))
        column.addView(filterRow)

        count.setTextColor(Color.rgb(255, 214, 120))
        count.textSize = 12f
        count.setPadding(act.dp(3), act.dp(5), 0, act.dp(4))
        column.addView(count)

        val scanControls = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        modeButton.text = "▦ Grade"; modeButton.textSize = 11f; modeButton.isAllCaps = false; modeButton.setTextColor(Color.WHITE)
        modeButton.background = rounded(Color.rgb(67, 94, 130), Color.rgb(67, 94, 130), 8, 1)
        modeButton.setOnClickListener { scanMode = !scanMode; modeButton.text = if (scanMode) "▦ Grade" else "☰ Lista"; list.visibility = if (scanMode) View.GONE else View.VISIBLE; grid.visibility = if (scanMode) View.VISIBLE else View.GONE }
        issueButton.text = "⚠ Índices inválidos"; issueButton.textSize = 11f; issueButton.isAllCaps = false; issueButton.setTextColor(Color.WHITE)
        issueButton.background = rounded(Color.rgb(110, 55, 58), Color.rgb(170, 75, 80), 8, 1)
        issueButton.setOnClickListener { onlyBad = !onlyBad; issueButton.text = if (onlyBad) "✕ Mostrar todos" else "⚠ Índices inválidos"; applyFilters() }
        scanControls.addView(modeButton, LinearLayout.LayoutParams(0, act.dp(38), 1f).apply { rightMargin = act.dp(4) })
        scanControls.addView(issueButton, LinearLayout.LayoutParams(0, act.dp(38), 1.5f).apply { leftMargin = act.dp(4) })
        column.addView(scanControls)

        val detailCard = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.rgb(27, 33, 44), Color.rgb(61, 73, 92), 12)
            setPadding(act.dp(8), act.dp(7), act.dp(8), act.dp(7))
        }
        detailCard.addView(cube, LinearLayout.LayoutParams(act.dp(88), act.dp(88)).apply { rightMargin = act.dp(8) })
        val detailRight = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        nameLine.setTextColor(Color.WHITE); nameLine.textSize = 15f; nameLine.typeface = android.graphics.Typeface.DEFAULT_BOLD
        detailsLine.setTextColor(Color.rgb(184, 195, 211)); detailsLine.textSize = 11f; detailsLine.maxLines = 2
        detailsLine.ellipsize = android.text.TextUtils.TruncateAt.END
        statusLine.setTextColor(Color.rgb(133, 225, 165)); statusLine.textSize = 11f
        statusLine.setPadding(0, act.dp(3), 0, 0)
        detailRight.addView(nameLine)
        detailRight.addView(detailsLine)
        detailRight.addView(statusLine)
        detailCard.addView(detailRight, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        column.addView(detailCard, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = act.dp(5) })

        val facesRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        val faceLabels = arrayOf("TOPO", "LATERAL", "BASE")
        for (i in faces.indices) {
            val faceCol = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                background = rounded(Color.rgb(25, 31, 41), Color.rgb(46, 55, 70), 8)
                setPadding(act.dp(4), act.dp(3), act.dp(4), act.dp(3))
            }
            val label = TextView(act).apply {
                text = faceLabels[i]; textSize = 10f; gravity = Gravity.CENTER
                setTextColor(Color.rgb(255, 214, 120))
            }
            faceIds[i].apply { textSize = 10f; gravity = Gravity.CENTER; setTextColor(Color.rgb(170, 185, 205)) }
            faceCol.addView(label)
            faceCol.addView(faces[i], LinearLayout.LayoutParams(act.dp(48), act.dp(48)))
            faceCol.addView(faceIds[i])
            facesRow.addView(faceCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(act.dp(3), 0, act.dp(3), 0)
            })
        }
        column.addView(facesRow, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = act.dp(5) })

        val actions = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        actions.addView(button("◀ Anterior", Color.rgb(68, 83, 106)) { cycle(-1) },
            LinearLayout.LayoutParams(0, act.dp(42), 1f).apply { rightMargin = act.dp(4) })
        actions.addView(button("TESTAR NO MUNDO", Color.rgb(226, 155, 49)) {
            if (selectedId > 0) onTest(selectedId)
        }, LinearLayout.LayoutParams(0, act.dp(42), 1.35f).apply { leftMargin = act.dp(2); rightMargin = act.dp(2) })
        actions.addView(button("Próximo ▶", Color.rgb(68, 83, 106)) { cycle(1) },
            LinearLayout.LayoutParams(0, act.dp(42), 1f).apply { leftMargin = act.dp(4) })
        column.addView(actions, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = act.dp(5) })

        list.divider = GradientDrawable().apply { setColor(Color.rgb(43, 51, 64)); setSize(1, act.dp(1)) }
        list.dividerHeight = act.dp(1)
        list.cacheColorHint = Color.TRANSPARENT
        list.setBackgroundColor(Color.rgb(17, 21, 29))
        list.setSelector(android.R.color.transparent)
        adapter = object : BaseAdapter() {
            override fun getCount() = filtered.size
            override fun getItem(position: Int) = filtered[position]
            override fun getItemId(position: Int) = filtered[position].id.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val item = filtered[position]
                val row: LinearLayout
                val holder: Holder
                if (convertView is LinearLayout && convertView.tag is Holder) {
                    row = convertView
                    holder = row.tag as Holder
                } else {
                    row = LinearLayout(act).apply {
                        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                        setPadding(act.dp(7), act.dp(5), act.dp(7), act.dp(5))
                    }
                    val preview = CubePreviewView(act)
                    val titleText = TextView(act).apply { setTextColor(Color.WHITE); textSize = 13f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
                    val metaText = TextView(act).apply { setTextColor(Color.rgb(169, 182, 202)); textSize = 11f }
                    row.addView(preview, LinearLayout.LayoutParams(act.dp(54), act.dp(54)).apply { rightMargin = act.dp(9) })
                    val words = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
                    words.addView(titleText); words.addView(metaText)
                    row.addView(words, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    holder = Holder(preview, titleText, metaText)
                    row.tag = holder
                }
                holder.cube.blockId = item.id
                holder.cube.invalidate()
                holder.name.text = item.name
                holder.meta.text = "ID ${item.id}  •  ${item.category}"
                row.background = rounded(
                    if (item.id == selectedId) Color.rgb(43, 54, 72) else Color.rgb(21, 27, 36),
                    if (item.id == selectedId) Color.rgb(255, 190, 80) else Color.rgb(38, 47, 61), 9, 1
                )
                return row
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            if (position in filtered.indices) {
                selectedId = filtered[position].id
                adapter.notifyDataSetChanged()
                updateDetails()
            }
        }
        column.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        grid.numColumns = 3; grid.horizontalSpacing = act.dp(5); grid.verticalSpacing = act.dp(5); grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.setPadding(0, act.dp(3), 0, act.dp(3)); grid.setBackgroundColor(Color.rgb(12, 16, 23))
        grid.adapter = object : BaseAdapter() {
            override fun getCount() = filtered.size
            override fun getItem(position: Int) = filtered[position]
            override fun getItemId(position: Int) = filtered[position].id.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val item = filtered[position]
                val card = (convertView as? LinearLayout) ?: LinearLayout(act).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(act.dp(4), act.dp(5), act.dp(4), act.dp(5))
                    val preview = CubePreviewView(act)
                    val name = TextView(act).apply { setTextColor(Color.WHITE); textSize = 10f; maxLines = 2; gravity = Gravity.CENTER; ellipsize = android.text.TextUtils.TruncateAt.END }
                    val meta = TextView(act).apply { textSize = 9f; gravity = Gravity.CENTER }
                    addView(preview, LinearLayout.LayoutParams(act.dp(64), act.dp(64))); addView(name); addView(meta)
                    tag = arrayOf(preview, name, meta)
                }
                val v = card.tag as Array<*>
                val preview = v[0] as CubePreviewView; val name = v[1] as TextView; val meta = v[2] as TextView
                val bad = hasInvalidTiles(item.id); preview.blockId = item.id; preview.invalidate(); name.text = item.name
                meta.text = if (bad) "⚠ ${item.id} • TILE INVÁLIDO" else "✓ ${item.id} • índices OK"
                meta.setTextColor(if (bad) Color.rgb(255, 105, 105) else Color.rgb(133, 225, 165))
                card.background = rounded(if (item.id == selectedId) Color.rgb(43, 54, 72) else Color.rgb(21, 27, 36), if (bad) Color.rgb(235, 72, 78) else if (item.id == selectedId) Color.rgb(255, 190, 80) else Color.rgb(55, 68, 86), 8, 2)
                return card
            }
        }
        gridAdapter = grid.adapter as BaseAdapter
        grid.setOnItemClickListener { _, _, position, _ -> if (position in filtered.indices) { selectedId = filtered[position].id; adapter.notifyDataSetChanged(); gridAdapter.notifyDataSetChanged(); updateDetails() } }
        column.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f)); grid.visibility = View.VISIBLE; list.visibility = View.GONE

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchText = s?.toString().orEmpty()
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        category.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                categoryPosition = position
                applyFilters()
            }
        }
        applyFilters()
    }

    fun show() { view.visibility = View.VISIBLE; applyFilters() }
    fun hide() { view.visibility = View.GONE }

    private fun button(text: String, color: Int, click: () -> Unit) =
        Button(act).apply {
            this.text = text; textSize = 11f; isAllCaps = false; setTextColor(Color.WHITE)
            background = rounded(color, color, 10, 1)
            minHeight = act.dp(40); minimumHeight = act.dp(40)
            setPadding(act.dp(4), 0, act.dp(4), 0)
            setOnClickListener { click() }
        }

    private fun rounded(fill: Int, stroke: Int, radius: Int, width: Int = 1): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill); cornerRadius = act.dp(radius).toFloat()
            setStroke(act.dp(width), stroke)
        }

    private fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    private fun applyFilters() {
        val q = normalize(searchText.trim())
        val selectedCategory = filters.getOrNull(categoryPosition) ?: "Todas"
        filtered = entries.filter { entry ->
            (selectedCategory == "Todas" || entry.category == selectedCategory) &&
                (q.isEmpty() || normalize(entry.name).contains(q) || entry.id.toString().contains(q))
        }
        if (filtered.none { it.id == selectedId }) selectedId = filtered.firstOrNull()?.id ?: -1
        if (onlyBad) filtered = filtered.filter { hasInvalidTiles(it.id) }
        if (::adapter.isInitialized) adapter.notifyDataSetChanged()
        if (::gridAdapter.isInitialized) gridAdapter.notifyDataSetChanged()
        count.text = "${filtered.size} exibidos • ${entries.size} no catálogo • vermelho = índice inválido"
        updateDetails()
    }

    private fun cycle(delta: Int) {
        if (filtered.isEmpty()) return
        val current = filtered.indexOfFirst { it.id == selectedId }.let { if (it < 0) 0 else it }
        selectedId = filtered[(current + delta + filtered.size) % filtered.size].id
        adapter.notifyDataSetChanged()
        updateDetails()
        list.setSelection(filtered.indexOfFirst { it.id == selectedId })
    }

    private fun hasInvalidTiles(id: Int): Boolean {
        if (id !in 1 until B.N) return true
        return intArrayOf(B.tTop[id], B.tSide[id], B.tBot[id]).any { it !in 0 until Blk.tileCount }
    }

    private fun updateDetails() {
        val item = entries.firstOrNull { it.id == selectedId }
        if (item == null) {
            cube.blockId = -1
            nameLine.text = "Nenhum bloco encontrado"
            detailsLine.text = "Tente outro nome, ID ou categoria."
            statusLine.text = "Lista vazia"
            statusLine.setTextColor(Color.rgb(255, 175, 100))
            for (i in faces.indices) { faces[i].blockId = -1; faces[i].face = i; faces[i].invalidate(); faceIds[i].text = "—" }
            cube.invalidate()
            return
        }
        val id = item.id
        cube.blockId = id; cube.invalidate()
        nameLine.text = item.name
        detailsLine.text = "ID $id • ${item.category}"
        val desc = B.desc.getOrNull(id).orEmpty()
        if (desc.isNotBlank()) detailsLine.append("\n$desc")
        val tiles = intArrayOf(B.tTop[id], B.tSide[id], B.tBot[id])
        var bad = false
        for (i in faces.indices) {
            faces[i].blockId = id; faces[i].face = i; faces[i].invalidate()
            faceIds[i].text = if (tiles[i] in 0 until Blk.tileCount) "Tile ${tiles[i]}" else "Tile INVÁLIDO"
            if (tiles[i] !in 0 until Blk.tileCount) bad = true
        }
        if (bad) {
            statusLine.text = "⚠ Referência fora do atlas: textura precisa de correção"
            statusLine.setTextColor(Color.rgb(255, 112, 112))
        } else {
            statusLine.text = "✓ Índices no atlas • confira as 3 faces visualmente"
            statusLine.setTextColor(Color.rgb(133, 225, 165))
        }
    }

    private fun tileFor(id: Int, face: Int): Int {
        if (id !in 1 until B.N) return -1
        return when (face) { 0 -> B.tTop[id]; 1 -> B.tSide[id]; else -> B.tBot[id] }
    }

    private fun checker(canvas: Canvas, rect: RectF, size: Float) {
        val p = Paint()
        val cols = intArrayOf(Color.rgb(210, 45, 112), Color.rgb(37, 41, 53))
        var y = rect.top
        var r = 0
        while (y < rect.bottom) {
            var x = rect.left
            var c = r
            while (x < rect.right) {
                p.color = cols[c and 1]
                canvas.drawRect(x, y, minOf(x + size, rect.right), minOf(y + size, rect.bottom), p)
                x += size; c++
            }
            y += size; r++
        }
    }

    private fun drawTile(canvas: Canvas, id: Int, face: Int, rect: RectF, paint: Paint) {
        checker(canvas, rect, act.dp(7).toFloat())
        val tile = tileFor(id, face)
        if (tile !in 0 until Blk.tileCount) return
        try {
            canvas.drawBitmap(Atlas.tile(tile), null, rect, paint)
        } catch (_: Throwable) {
            // Mantém o xadrez de diagnóstico visível se a textura também falhar no preview.
        }
    }

    private inner class TextureTileView(context: android.content.Context) : View(context) {
        var blockId = -1
        var face = 0
        private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            drawTile(canvas, blockId, face, RectF(0f, 0f, width.toFloat(), height.toFloat()), paint)
        }
    }

    private inner class CubePreviewView(context: android.content.Context) : View(context) {
        var blockId = -1
        private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            if (w <= 1f || h <= 1f) return
            val pad = act.dp(3).toFloat()
            checker(canvas, RectF(pad, pad, w - pad, h - pad), act.dp(8).toFloat())
            val top = Path().apply {
                moveTo(w * 0.5f, h * 0.07f); lineTo(w * 0.93f, h * 0.29f)
                lineTo(w * 0.5f, h * 0.51f); lineTo(w * 0.07f, h * 0.29f); close()
            }
            val left = Path().apply {
                moveTo(w * 0.07f, h * 0.29f); lineTo(w * 0.5f, h * 0.51f)
                lineTo(w * 0.5f, h * 0.93f); lineTo(w * 0.07f, h * 0.71f); close()
            }
            val right = Path().apply {
                moveTo(w * 0.5f, h * 0.51f); lineTo(w * 0.93f, h * 0.29f)
                lineTo(w * 0.93f, h * 0.71f); lineTo(w * 0.5f, h * 0.93f); close()
            }
            val faces = arrayOf(top, left, right)
            val faceTiles = intArrayOf(tileFor(blockId, 0), tileFor(blockId, 1), tileFor(blockId, 2))
            val rect = RectF(pad, pad, w - pad, h - pad)
            for (i in faces.indices) {
                canvas.save()
                canvas.clipPath(faces[i])
                checker(canvas, rect, act.dp(8).toFloat())
                val tile = faceTiles[i]
                if (tile in 0 until Blk.tileCount) {
                    try { canvas.drawBitmap(Atlas.tile(tile), null, rect, paint) } catch (_: Throwable) {}
                }
                canvas.restore()
                paint.color = Color.rgb(11, 14, 20); paint.style = Paint.Style.STROKE; paint.strokeWidth = act.dp(1).toFloat()
                canvas.drawPath(faces[i], paint)
                paint.style = Paint.Style.FILL
            }
        }
    }
}
