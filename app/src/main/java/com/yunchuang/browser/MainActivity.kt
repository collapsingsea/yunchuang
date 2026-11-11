package com.yunchuang.browser

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var store: SiteStore
    private lateinit var adapter: SiteAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: View
    private lateinit var txtSubtitle: TextView
    private lateinit var edtQuick: EditText
    private lateinit var dragHelper: ItemTouchHelper
    private var dragSiteId: String? = null
    private var dragMoved = false
    private var dragCancelled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        store = SiteStore(this)
        store.seedIfNeeded()

        recycler = findViewById(R.id.recycler)
        emptyView = findViewById(R.id.emptyView)
        txtSubtitle = findViewById(R.id.txtSubtitle)
        edtQuick = findViewById(R.id.edtQuick)

        setupInsets()
        setupStatusBarStyle()
        setupHeader()
        setupList()
        setupQuickOpen()

        findViewById<ExtendedFloatingActionButton>(R.id.fabAdd).setOnClickListener {
            startActivity(Intent(this, SiteEditorActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun setupInsets() {
        val header = findViewById<View>(R.id.headerArea)
        val fab = findViewById<View>(R.id.fabAdd)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.homeRoot)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.updatePadding(top = Ui.dp(this, 20f) + bars.top)
            recycler.updatePadding(bottom = Ui.dp(this, 120f) + bars.bottom)
            (fab.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin =
                Ui.dp(this, 20f) + bars.bottom
            fab.requestLayout()
            insets
        }
    }

    private fun setupStatusBarStyle() {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
    }

    private fun setupHeader() {
        val title = findViewById<TextView>(R.id.txtTitle)
        title.post {
            Ui.applyTitleGradient(
                title,
                ContextCompat.getColor(this, R.color.primary),
                ContextCompat.getColor(this, R.color.titleGradEnd)
            )
        }
        findViewById<ImageButton>(R.id.btnAbout).setOnClickListener { showAbout() }
    }

    private fun setupList() {
        adapter = SiteAdapter(
            onClick = { site -> openSite(site) },
            onLongClick = { site -> showSiteMenu(site) },
            onIconDrag = { holder ->
                dragSiteId = holder.itemView.tag as? String
                dragMoved = false
                dragCancelled = false
                dragHelper.startDrag(holder)
            }
        )
        recycler.layoutManager = GridLayoutManager(this, if (resources.configuration.screenWidthDp >= 600) 4 else 2)
        recycler.adapter = adapter
        recycler.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (e.actionMasked == MotionEvent.ACTION_CANCEL) dragCancelled = true
                return false
            }
        })

        dragHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun isLongPressDragEnabled(): Boolean = false

            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int = makeMovementFlags(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                    ItemTouchHelper.START or ItemTouchHelper.END,
                0
            )

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                if (viewHolder.itemViewType != target.itemViewType) return false
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                dragMoved = true
                adapter.move(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun onChildDraw(c: Canvas, rv: RecyclerView, holder: RecyclerView.ViewHolder,
                dX: Float, dY: Float, state: Int, active: Boolean) {
                val slop = ViewConfiguration.get(this@MainActivity).scaledTouchSlop
                if (active && (kotlin.math.abs(dX) > slop || kotlin.math.abs(dY) > slop)) dragMoved = true
                super.onChildDraw(c, rv, holder, dX, dY, state, active)
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    viewHolder.itemView.animate()
                        .scaleX(1.04f).scaleY(1.04f).alpha(0.92f)
                        .setDuration(160).start()
                    viewHolder.itemView.elevation = Ui.dp(this@MainActivity, 12f).toFloat()
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.animate()
                    .scaleX(1f).scaleY(1f).alpha(1f).setDuration(180).start()
                viewHolder.itemView.elevation = 0f

                val id = dragSiteId
                if (id != null && dragMoved) store.replaceOrder(adapter.snapshot())
                dragSiteId = null
                if (!dragMoved && !dragCancelled && id != null && !isFinishing) {
                    store.get(id)?.let { openEditor(it) }
                }
            }
        })
        dragHelper.attachToRecyclerView(recycler)
    }

    private fun setupQuickOpen() {
        val go = {
            val url = Site.normalizeUrl(edtQuick.text?.toString().orEmpty())
            if (url.isBlank()) {
                Toast.makeText(this, getString(R.string.enter_url), Toast.LENGTH_SHORT).show()
            } else {
                edtQuick.setText("")
                startActivity(
                    Intent(this, BrowserActivity::class.java)
                        .putExtra(BrowserActivity.EXTRA_URL, url)
                )
            }
        }
        findViewById<ImageButton>(R.id.btnQuickGo).setOnClickListener { go() }
        edtQuick.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                go()
                true
            } else false
        }
    }

    private fun refreshList() {
        val sites = store.list()
        adapter.submit(sites)
        emptyView.visibility = if (sites.isEmpty()) View.VISIBLE else View.GONE
        txtSubtitle.text = if (sites.isEmpty()) {
            getString(R.string.home_slogan)
        } else {
            getString(R.string.site_count, sites.size)
        }
    }

    private fun openSite(site: Site) {
        startActivity(
            Intent(this, BrowserActivity::class.java)
                .setData(Uri.parse("yunchuang://site/${site.id}"))
                .putExtra(BrowserActivity.EXTRA_SITE_ID, site.id)
        )
    }

    private fun openEditor(site: Site) {
        startActivity(
            Intent(this, SiteEditorActivity::class.java)
                .putExtra(SiteEditorActivity.EXTRA_SITE_ID, site.id)
        )
    }

    private fun showSiteMenu(site: Site) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_site_menu, null)
        dialog.setContentView(view)

        Ui.bindTile(view.findViewById(R.id.sheetTile), site, 13f)
        view.findViewById<TextView>(R.id.sheetName).text = site.name.ifBlank { site.host() }
        view.findViewById<TextView>(R.id.sheetHost).text = site.url

        fun bind(id: Int, action: () -> Unit) {
            view.findViewById<View>(id).setOnClickListener {
                dialog.dismiss()
                action()
            }
        }
        bind(R.id.rowOpen) { openSite(site) }
        bind(R.id.rowEdit) {
            startActivity(
                Intent(this, SiteEditorActivity::class.java)
                    .putExtra(SiteEditorActivity.EXTRA_SITE_ID, site.id)
            )
        }
        bind(R.id.rowPinTop) {
            store.moveToTop(site.id)
            refreshList()
        }
        bind(R.id.rowShortcut) { pinShortcut(site) }
        bind(R.id.rowCopy) { Ui.copyText(this, site.url) }
        bind(R.id.rowDelete) { confirmDelete(site) }

        dialog.show()
    }

    private fun confirmDelete(site: Site) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_site))
            .setMessage(getString(R.string.delete_message, site.name.ifBlank { site.host() }))
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                store.delete(site.id)
                refreshList()
                Toast.makeText(this, getString(R.string.deleted), Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    /** 把站点钉到系统桌面，变成一个"独立应用"入口 */
    private fun pinShortcut(site: Site) {
        val sm = getSystemService(ShortcutManager::class.java)
        if (sm == null || !sm.isRequestPinShortcutSupported) {
            Toast.makeText(this, getString(R.string.shortcut_unsupported), Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, BrowserActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("yunchuang://site/${site.id}")
            putExtra(BrowserActivity.EXTRA_SITE_ID, site.id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val label = site.name.ifBlank { site.host() }
        val info = ShortcutInfo.Builder(this, "site_${site.id}")
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(Icon.createWithAdaptiveBitmap(Ui.tileBitmap(site)))
            .setIntent(intent)
            .build()
        try {
            sm.requestPinShortcut(info, null)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.shortcut_failed, e.message.orEmpty()), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAbout() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "?"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.about))
            .setMessage(getString(R.string.about_message, version))
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }
}
