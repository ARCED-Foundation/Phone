package org.fossify.phone.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import me.grantland.widget.AutofitHelper
import org.fossify.commons.dialogs.ChangeViewTypeDialog
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.PermissionRequiredDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.models.FAQItem
import org.fossify.commons.models.RadioItem
import org.fossify.commons.models.contacts.Contact
import org.fossify.phone.BuildConfig
import org.fossify.phone.R
import org.fossify.phone.adapters.ViewPagerAdapter
import org.fossify.phone.databinding.ActivityMainBinding
import org.fossify.phone.dialogs.ChangeSortingDialog
import org.fossify.phone.dialogs.FilterContactSourcesDialog
import org.fossify.phone.extensions.clearMissedCalls
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.handleFullScreenNotificationsPermission
import org.fossify.phone.extensions.launchCreateNewContactIntent
import org.fossify.phone.fragments.ContactsFragment
import org.fossify.phone.fragments.FavoritesFragment
import org.fossify.phone.fragments.MyViewPagerFragment
import org.fossify.phone.fragments.RecentsFragment
import org.fossify.phone.services.CallSyncService
import org.fossify.phone.helpers.ContactCacheManager
import org.fossify.phone.helpers.OPEN_DIAL_PAD_AT_LAUNCH
import org.fossify.phone.helpers.AdminSettingsHelper
import org.fossify.phone.helpers.RecentsHelper
import org.fossify.phone.helpers.SyncStatusTracker
import org.fossify.phone.helpers.tabsList
import org.fossify.phone.helpers.CallManager
import org.fossify.phone.models.Events
import org.fossify.phone.database.AppDatabase
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MainActivity : SimpleActivity() {
    override var isSearchBarEnabled = true
    
    private val binding by viewBinding(ActivityMainBinding::inflate)

    private var launchedDialer = false
    private var storedShowTabs = 0
    private var storedFontSize = 0
    private var storedStartNameWithSurname = false
    var cachedContacts = ArrayList<Contact>()
    private var syncStatusJob: Job? = null
    private var credentialsCheckJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupOptionsMenu()
        refreshMenuItems()
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.mainTabsHolder))
        startSyncStatusObserver()
        binding.mainSyncStatus.setOnClickListener { triggerManualSync() }

        EventBus.getDefault().register(this)
        launchedDialer = savedInstanceState?.getBoolean(OPEN_DIAL_PAD_AT_LAUNCH) ?: false

        if (isDefaultDialer()) {
            checkContactPermissions()

            if (!config.wasOverlaySnackbarConfirmed && !Settings.canDrawOverlays(this)) {
                val snackbar = Snackbar.make(
                    binding.mainHolder,
                    R.string.allow_displaying_over_other_apps,
                    Snackbar.LENGTH_INDEFINITE
                ).setAction(R.string.ok) {
                    config.wasOverlaySnackbarConfirmed = true
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                }

                snackbar.setBackgroundTint(getProperBackgroundColor().darkenColor())
                snackbar.setTextColor(getProperTextColor())
                snackbar.setActionTextColor(getProperTextColor())
                snackbar.show()
            }

            handleFullScreenNotificationsPermission { granted ->
                if (!granted) {
                    toast(org.fossify.commons.R.string.notifications_disabled)
                }
            }
        } else {
            launchSetDefaultDialerIntent()
        }

        if (isQPlus() && (config.blockUnknownNumbers || config.blockHiddenNumbers)) {
            setDefaultCallerIdApp()
        }

        setupTabs()
        Contact.sorting = config.sorting

        // Preload contacts in background for better performance
        preloadContacts()
    }

    override fun onResume() {
        super.onResume()
        if (storedShowTabs != config.showTabs) {
            config.lastUsedViewPagerPage = 0
            System.exit(0)
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Enhanced ODK state checking to prevent forms for ODK calls
                val hasPendingOdkCalls = CallManager.hasPendingOdkCalls()
                val isOdkSessionActive = CallManager.isOdkSessionActive()
                val isOdkSessionReady = CallManager.isOdkSessionReady()
                val odkSessionState = CallManager.getOdkSessionState()

                android.util.Log.d("MainActivity", "Form trigger check - ODK State: $odkSessionState, hasPending: $hasPendingOdkCalls, sessionActive: $isOdkSessionActive, sessionReady: $isOdkSessionReady")

                // Check if there are calls that need PostCallMetadataActivity form
                val db = AppDatabase.getInstance(this@MainActivity)
                val callNeedingForm = db.callLogDao().getCallNeedingForm()
                val callWithIncompleteForm = db.callLogDao().getCallWithIncompleteForm()

                // Only launch survey if no pending ODK calls and no valid ODK session
                // AND no pending calls that need PostCallMetadataActivity form
                if (!hasPendingOdkCalls && !isOdkSessionReady && callNeedingForm == null && callWithIncompleteForm == null) {
                    android.util.Log.d("MainActivity", "Launching survey data collection - no ODK activity detected and no calls needing form")
                    SurveyDataCollectionActivity.launchPendingIfAny(this@MainActivity)
                } else {
                    android.util.Log.d("MainActivity", "Skipping survey data collection - ODK activity detected${if (callNeedingForm != null) " or calls needing PostCallMetadataActivity form" else if (callWithIncompleteForm != null) " or calls with incomplete form" else ""}")

                    // If there are calls with incomplete forms, show a notification
                    callWithIncompleteForm?.let { call ->
                        withContext(Dispatchers.Main) {
                            showIncompleteFormNotification(call)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to launch survey data collection", e)
                // Don't crash the app, just log the error and continue
            }
        }

        updateMenuColors()
        val properPrimaryColor = getProperPrimaryColor()
        val dialpadIcon = resources.getColoredDrawableWithColor(R.drawable.ic_dialpad_vector, properPrimaryColor.getContrastColor())
        binding.mainDialpadButton.setImageDrawable(dialpadIcon)

        // Check credentials status
        checkCredentialsStatus()

        updateTextColors(binding.mainHolder)
        setupTabColors()

        getAllFragments().forEach {
            it?.setupColors(getProperTextColor(), getProperPrimaryColor(), getProperPrimaryColor())
        }

        val configStartNameWithSurname = config.startNameWithSurname
        if (storedStartNameWithSurname != configStartNameWithSurname) {
            getContactsFragment()?.startNameWithSurnameChanged(configStartNameWithSurname)
            getFavoritesFragment()?.startNameWithSurnameChanged(configStartNameWithSurname)
            storedStartNameWithSurname = config.startNameWithSurname
        }

        if (!binding.mainMenu.isSearchOpen) {
            refreshItems(true)
        }

        val configFontSize = config.fontSize
        if (storedFontSize != configFontSize) {
            getAllFragments().forEach {
                it?.fontSizeChanged()
            }
        }

        checkShortcuts()
        Handler().postDelayed({
            getRecentsFragment()?.refreshItems()
        }, 2000)
    }

    override fun onPause() {
        super.onPause()
        storedShowTabs = config.showTabs
        storedStartNameWithSurname = config.startNameWithSurname
        config.lastUsedViewPagerPage = binding.viewPager.currentItem
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        // we don't really care about the result, the app can work without being the default Dialer too
        if (requestCode == REQUEST_CODE_SET_DEFAULT_DIALER) {
            checkContactPermissions()
        } else if (requestCode == REQUEST_CODE_SET_DEFAULT_CALLER_ID && resultCode != Activity.RESULT_OK) {
            toast(R.string.must_make_default_caller_id_app, length = Toast.LENGTH_LONG)
            baseConfig.blockUnknownNumbers = false
            baseConfig.blockHiddenNumbers = false
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(OPEN_DIAL_PAD_AT_LAUNCH, launchedDialer)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshItems()
    }

    override fun onBackPressedCompat(): Boolean {
        return if (binding.mainMenu.isSearchOpen) {
            binding.mainMenu.closeSearch()
            true
        } else {
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
        stopSyncStatusObserver()
        credentialsCheckJob?.cancel()
    }

    private fun refreshMenuItems() {
        val currentFragment = getCurrentFragment()
        binding.mainMenu.requireToolbar().menu.apply {
            findItem(R.id.clear_call_history).isVisible = currentFragment == getRecentsFragment()
            findItem(R.id.sort).isVisible = currentFragment != getRecentsFragment()
            findItem(R.id.filter).isVisible = currentFragment != getRecentsFragment()
            findItem(R.id.create_new_contact).isVisible = currentFragment == getContactsFragment()
            findItem(R.id.change_view_type).isVisible = currentFragment == getFavoritesFragment()
            findItem(R.id.column_count).isVisible = currentFragment == getFavoritesFragment() && config.viewType == VIEW_TYPE_GRID
        }
    }

    private fun setupOptionsMenu() {
        binding.mainMenu.apply {
            requireToolbar().inflateMenu(R.menu.menu)
            toggleHideOnScroll(false)
            setupMenu()

            onSearchClosedListener = {
                getAllFragments().forEach {
                    it?.onSearchQueryChanged("")
                }
            }

            onSearchTextChangedListener = { text ->
                getCurrentFragment()?.onSearchQueryChanged(text)
            }

            requireToolbar().setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.clear_call_history -> clearCallHistory()
                    R.id.create_new_contact -> launchCreateNewContactIntent()
                    R.id.sort -> showSortingDialog(showCustomSorting = getCurrentFragment() is FavoritesFragment)
                    R.id.filter -> showFilterDialog()
                    R.id.admin_settings -> launchAdminSettings()
                    R.id.sync_logs -> launchSyncLogs()
                    R.id.settings -> launchSettings()
                    R.id.change_view_type -> changeViewType()
                    R.id.column_count -> changeColumnCount()
                    R.id.about -> launchAbout()
                    else -> return@setOnMenuItemClickListener false
                }
                return@setOnMenuItemClickListener true
            }
        }
    }

    private fun changeColumnCount() {
        val items = ArrayList<RadioItem>()
        for (i in 1..CONTACTS_GRID_MAX_COLUMNS_COUNT) {
            items.add(RadioItem(i, resources.getQuantityString(R.plurals.column_counts, i, i)))
        }

        val currentColumnCount = config.contactsGridColumnCount
        RadioGroupDialog(this, ArrayList(items), currentColumnCount) {
            val newColumnCount = it as Int
            if (currentColumnCount != newColumnCount) {
                config.contactsGridColumnCount = newColumnCount
                getFavoritesFragment()?.columnCountChanged()
            }
        }
    }

    private fun changeViewType() {
        ChangeViewTypeDialog(this) {
            refreshMenuItems()
            getFavoritesFragment()?.refreshItems()
        }
    }

    private fun updateMenuColors() {
        binding.mainMenu.updateColors()
    }

    private fun checkContactPermissions() {
        handlePermission(PERMISSION_READ_CONTACTS) {
            initFragments()
        }
    }

    private fun clearCallHistory() {
        val confirmationText = "${getString(R.string.clear_history_confirmation)}\n\n${getString(R.string.cannot_be_undone)}"
        ConfirmationDialog(this, confirmationText) {
            RecentsHelper(this).removeAllRecentCalls(this) {
                runOnUiThread {
                    getRecentsFragment()?.refreshItems(invalidate = true)
                }
            }
        }
    }

    @SuppressLint("NewApi")
    private fun checkShortcuts() {
        val appIconColor = config.appIconColor
        if (isNougatMR1Plus() && config.lastHandledShortcutColor != appIconColor) {
            val launchDialpad = getLaunchDialpadShortcut(appIconColor)

            try {
                shortcutManager.dynamicShortcuts = listOf(launchDialpad)
                config.lastHandledShortcutColor = appIconColor
            } catch (ignored: Exception) {
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getLaunchDialpadShortcut(appIconColor: Int): ShortcutInfo {
        val newEvent = getString(R.string.dialpad)
        val drawable = resources.getDrawable(R.drawable.shortcut_dialpad)
        (drawable as LayerDrawable).findDrawableByLayerId(R.id.shortcut_dialpad_background).applyColorFilter(appIconColor)
        val bmp = drawable.convertToBitmap()

        val intent = Intent(this, DialpadActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        return ShortcutInfo.Builder(this, "launch_dialpad")
            .setShortLabel(newEvent)
            .setLongLabel(newEvent)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(intent)
            .build()
    }

    private fun setupTabColors() {
        val activeView = binding.mainTabsHolder.getTabAt(binding.viewPager.currentItem)?.customView
        updateBottomTabItemColors(activeView, true, getSelectedTabDrawableIds()[binding.viewPager.currentItem])

        getInactiveTabIndexes(binding.viewPager.currentItem).forEach { index ->
            val inactiveView = binding.mainTabsHolder.getTabAt(index)?.customView
            updateBottomTabItemColors(inactiveView, false, getDeselectedTabDrawableIds()[index])
        }

        val bottomBarColor = getBottomNavigationBackgroundColor()
        binding.mainTabsHolder.setBackgroundColor(bottomBarColor)
    }

    private fun getInactiveTabIndexes(activeIndex: Int) = (0 until binding.mainTabsHolder.tabCount).filter { it != activeIndex }

    private fun getSelectedTabDrawableIds(): List<Int> {
        val showTabs = config.showTabs
        val icons = mutableListOf<Int>()

        if (showTabs and TAB_CONTACTS != 0) {
            icons.add(R.drawable.ic_person_vector)
        }

        if (showTabs and TAB_FAVORITES != 0) {
            icons.add(R.drawable.ic_star_vector)
        }

        if (showTabs and TAB_CALL_HISTORY != 0) {
            icons.add(R.drawable.ic_clock_filled_vector)
        }

        return icons
    }

    private fun getDeselectedTabDrawableIds(): ArrayList<Int> {
        val showTabs = config.showTabs
        val icons = ArrayList<Int>()

        if (showTabs and TAB_CONTACTS != 0) {
            icons.add(R.drawable.ic_person_outline_vector)
        }

        if (showTabs and TAB_FAVORITES != 0) {
            icons.add(R.drawable.ic_star_outline_vector)
        }

        if (showTabs and TAB_CALL_HISTORY != 0) {
            icons.add(R.drawable.ic_clock_vector)
        }

        return icons
    }

    private fun initFragments() {
        binding.viewPager.offscreenPageLimit = 1
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                binding.mainTabsHolder.getTabAt(position)?.select()
                getAllFragments().forEach {
                    it?.finishActMode()
                }
                refreshMenuItems()
            }
        })

        // selecting the proper tab sometimes glitches, add an extra selector to make sure we have it right
        binding.mainTabsHolder.onGlobalLayout {
            Handler().postDelayed({
                var wantedTab = getDefaultTab()

                // open the Recents tab if we got here by clicking a missed call notification
                if (intent.action == Intent.ACTION_VIEW && config.showTabs and TAB_CALL_HISTORY > 0) {
                    wantedTab = binding.mainTabsHolder.tabCount - 1
                }

                binding.mainTabsHolder.getTabAt(wantedTab)?.select()
                refreshMenuItems()
            }, 100L)
        }

        binding.mainDialpadButton.setOnClickListener {
            launchDialpad()
        }

        binding.viewPager.onGlobalLayout {
            refreshMenuItems()
        }

        if (config.openDialPadAtLaunch && !launchedDialer) {
            launchDialpad()
            launchedDialer = true
        }
    }

    private fun setupTabs() {
        binding.viewPager.adapter = null
        binding.mainTabsHolder.removeAllTabs()
        tabsList.forEachIndexed { index, value ->
            if (config.showTabs and value != 0) {
                binding.mainTabsHolder.newTab().setCustomView(R.layout.bottom_tablayout_item).apply {
                    customView?.findViewById<ImageView>(R.id.tab_item_icon)?.setImageDrawable(getTabIcon(index))
                    customView?.findViewById<TextView>(R.id.tab_item_label)?.text = getTabLabel(index)
                    AutofitHelper.create(customView?.findViewById(R.id.tab_item_label))
                    binding.mainTabsHolder.addTab(this)
                }
            }
        }

        binding.mainTabsHolder.onTabSelectionChanged(
            tabUnselectedAction = {
                updateBottomTabItemColors(it.customView, false, getDeselectedTabDrawableIds()[it.position])
            },
            tabSelectedAction = {
                getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                binding.viewPager.currentItem = it.position
                updateBottomTabItemColors(it.customView, true, getSelectedTabDrawableIds()[it.position])

                val lastPosition = binding.mainTabsHolder.tabCount - 1
                if (it.position == lastPosition && config.showTabs and TAB_CALL_HISTORY > 0) {
                    clearMissedCalls()
                }
            }
        )

        binding.mainTabsHolder.beGoneIf(binding.mainTabsHolder.tabCount == 1)
        storedShowTabs = config.showTabs
        storedStartNameWithSurname = config.startNameWithSurname
    }

    private fun getTabIcon(position: Int): Drawable {
        val drawableId = when (position) {
            0 -> R.drawable.ic_person_vector
            1 -> R.drawable.ic_star_vector
            else -> R.drawable.ic_clock_vector
        }

        return resources.getColoredDrawableWithColor(drawableId, getProperTextColor())
    }

    private fun getTabLabel(position: Int): String {
        val stringId = when (position) {
            0 -> R.string.contacts_tab
            1 -> R.string.favorites_tab
            else -> R.string.call_history_tab
        }

        return resources.getString(stringId)
    }

    private fun refreshItems(openLastTab: Boolean = false) {
        if (isDestroyed || isFinishing) {
            return
        }

        binding.apply {
            if (viewPager.adapter == null) {
                viewPager.adapter = ViewPagerAdapter(this@MainActivity)
                viewPager.currentItem = if (openLastTab) config.lastUsedViewPagerPage else getDefaultTab()
                viewPager.onGlobalLayout {
                    refreshFragments()
                }
            } else {
                refreshFragments()
            }
        }
    }

    private fun launchDialpad() {
        Intent(applicationContext, DialpadActivity::class.java).apply {
            startActivity(this)
        }
    }

    fun refreshFragments() {
        cacheContacts()
        getContactsFragment()?.refreshItems()
        getFavoritesFragment()?.refreshItems()
        getRecentsFragment()?.refreshItems()
    }

    private fun getAllFragments(): ArrayList<MyViewPagerFragment<*>?> {
        val showTabs = config.showTabs
        val fragments = arrayListOf<MyViewPagerFragment<*>?>()

        if (showTabs and TAB_CONTACTS > 0) {
            fragments.add(getContactsFragment())
        }

        if (showTabs and TAB_FAVORITES > 0) {
            fragments.add(getFavoritesFragment())
        }

        if (showTabs and TAB_CALL_HISTORY > 0) {
            fragments.add(getRecentsFragment())
        }

        return fragments
    }

    private fun getCurrentFragment(): MyViewPagerFragment<*>? = getAllFragments().getOrNull(binding.viewPager.currentItem)

    private fun getContactsFragment(): ContactsFragment? = findViewById(R.id.contacts_fragment)

    private fun getFavoritesFragment(): FavoritesFragment? = findViewById(R.id.favorites_fragment)

    private fun getRecentsFragment(): RecentsFragment? = findViewById(R.id.recents_fragment)

    private fun getDefaultTab(): Int {
        val showTabsMask = config.showTabs
        return when (config.defaultTab) {
            TAB_LAST_USED -> if (config.lastUsedViewPagerPage < binding.mainTabsHolder.tabCount) config.lastUsedViewPagerPage else 0
            TAB_CONTACTS -> 0
            TAB_FAVORITES -> if (showTabsMask and TAB_CONTACTS > 0) 1 else 0
            else -> {
                if (showTabsMask and TAB_CALL_HISTORY > 0) {
                    if (showTabsMask and TAB_CONTACTS > 0) {
                        if (showTabsMask and TAB_FAVORITES > 0) {
                            2
                        } else {
                            1
                        }
                    } else {
                        if (showTabsMask and TAB_FAVORITES > 0) {
                            1
                        } else {
                            0
                        }
                    }
                } else {
                    0
                }
            }
        }
    }

    private fun launchAdminSettings() {
        hideKeyboard()
        startActivity(Intent(applicationContext, AdminSetupActivity::class.java))
    }

    private fun launchSettings() {
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun launchAbout() {
        val licenses = LICENSE_GLIDE or LICENSE_INDICATOR_FAST_SCROLL or LICENSE_AUTOFITTEXTVIEW

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_1_title, R.string.faq_1_text),
            FAQItem(R.string.faq_2_title, R.string.faq_2_text),
            FAQItem(R.string.faq_3_title, R.string.faq_3_text),
            FAQItem(R.string.faq_9_title_commons, R.string.faq_9_text_commons)
        )

        if (!resources.getBoolean(R.bool.hide_google_relations)) {
            faqItems.add(FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons))
            faqItems.add(FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons))
        }

        startAboutActivity(R.string.app_name, licenses, BuildConfig.VERSION_NAME, faqItems, true)
    }

    private fun showSortingDialog(showCustomSorting: Boolean) {
        ChangeSortingDialog(this, showCustomSorting) {
            getFavoritesFragment()?.refreshItems {
                if (binding.mainMenu.isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }
            }

            getContactsFragment()?.refreshItems {
                if (binding.mainMenu.isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }
            }
        }
    }

    private fun showFilterDialog() {
        FilterContactSourcesDialog(this) {
            getFavoritesFragment()?.refreshItems {
                if (binding.mainMenu.isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }
            }

            getContactsFragment()?.refreshItems {
                if (binding.mainMenu.isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }
            }

            getRecentsFragment()?.refreshItems {
                if (binding.mainMenu.isSearchOpen) {
                    getCurrentFragment()?.onSearchQueryChanged(binding.mainMenu.getCurrentQuery())
                }
            }
        }
    }

    fun cacheContacts() {
        // Use ContactCacheManager for efficient contact loading
        val contactCache = ContactCacheManager.getInstance(this)

        lifecycleScope.launchWhenStarted {
            val contacts = contactCache.getContacts()
            try {
                cachedContacts.clear()
                cachedContacts.addAll(contacts)
            } catch (ignored: Exception) {
                // Fallback to old method if cache fails
                loadContactsFallback()
            }
        }
    }

    private fun loadContactsFallback() {
        val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        ContactsHelper(this).getContacts(getAll = true, showOnlyContactsWithNumbers = true) { contacts ->
            if (SMT_PRIVATE !in config.ignoredContactSources) {
                val privateContacts = MyContactsContentProvider.getContacts(this, privateCursor)
                if (privateContacts.isNotEmpty()) {
                    contacts.addAll(privateContacts)
                    contacts.sort()
                }
            }

            try {
                cachedContacts.clear()
                cachedContacts.addAll(contacts)
            } catch (ignored: Exception) {
            }
        }
    }

    fun preloadContacts() {
        // Preload contacts in background using ContactCacheManager
        ContactCacheManager.getInstance(this).preloadContacts()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshCallLog(event: Events.RefreshCallLog) {
        getRecentsFragment()?.refreshItems()
    }

    private fun startSyncStatusObserver() {
        syncStatusJob?.cancel()
        syncStatusJob = lifecycleScope.launchWhenStarted {
            SyncStatusTracker.create(this@MainActivity).collect { status ->
                updateSyncStatusBar(status)
            }
        }
    }

    private fun stopSyncStatusObserver() {
        syncStatusJob?.cancel()
        syncStatusJob = null
    }

    private fun updateSyncStatusBar(status: SyncStatusTracker.SyncStatus) {
        when {
            !status.lastError.isNullOrBlank() -> showSyncStatusBar(
                message = getString(R.string.phone_sync_status_failed, status.lastError),
                color = ContextCompat.getColor(this, R.color.color_missed_call)
            )
            status.pendingCount > 0 -> showSyncStatusBar(
                message = getString(R.string.phone_sync_status_pending, status.pendingCount),
                color = ContextCompat.getColor(this, R.color.color_incoming_call)
            )
            else -> hideSyncStatusBar()
        }
    }

    private fun showSyncStatusBar(message: String, @ColorInt color: Int) {
        binding.mainSyncStatusText.text = message
        binding.mainSyncStatusIcon.imageTintList = ColorStateList.valueOf(color)
        binding.mainSyncStatusText.setTextColor(color)
        binding.mainSyncStatus.beVisible()
    }

    private fun hideSyncStatusBar() {
        binding.mainSyncStatus.beGone()
    }

    private fun triggerManualSync() {
        try {
            val intent = Intent(this, CallSyncService::class.java)
            ContextCompat.startForegroundService(this, intent)
            toast(R.string.phone_sync_status_retrying)
        } catch (e: Exception) {
            toast(R.string.phone_sync_status_retry_failed)
        }
    }

    private fun launchSyncLogs() {
        try {
            startActivity(Intent(this, SyncLogActivity::class.java))
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Unable to open sync logs", e)
            toast(R.string.connection_failed)
        }
    }

    private fun showIncompleteFormNotification(call: org.fossify.phone.models.CallLog) {
        android.util.Log.d("MainActivity", "Showing incomplete form notification for call: ${call.callLogId}")

        // Show a snackbar notification about the incomplete form
        val snackbar = Snackbar.make(
            binding.mainHolder,
            getString(R.string.incomplete_form_message, call.phoneNumber ?: "Unknown number"),
            Snackbar.LENGTH_INDEFINITE
        ).setAction(R.string.complete_form) {
            // Launch the PostCallMetadataActivity for this call
            PostCallMetadataActivity.launch(this, call.callLogId)
        }

        // Style the snackbar to match the app theme
        snackbar.setBackgroundTint(getProperBackgroundColor().darkenColor())
        snackbar.setTextColor(getProperTextColor())
        snackbar.setActionTextColor(getProperPrimaryColor())

        // Show the snackbar
        snackbar.show()
    }

    private fun checkCredentialsStatus() {
        credentialsCheckJob?.cancel()
        credentialsCheckJob = lifecycleScope.launchWhenStarted {
            val adminHelper = AdminSettingsHelper(this@MainActivity)
            val hasValidCredentials = adminHelper.hasValidCredentials()

            android.util.Log.d("MainActivity", "Credentials status check - hasValidCredentials: $hasValidCredentials")

            if (!hasValidCredentials) {
                showCredentialsBanner()
            } else {
                hideCredentialsBanner()
            }
        }
    }

    private var credentialsBannerView: View? = null

    private fun showCredentialsBanner() {
        android.util.Log.d("MainActivity", "Showing credentials status banner")

        // Remove existing banner if present
        hideCredentialsBanner()

        // Inflate and show custom banner
        val bannerView = LayoutInflater.from(this).inflate(R.layout.layout_credentials_banner, binding.mainHolder, false)
        val clickTarget = bannerView.findViewById<View>(R.id.credentials_banner_content)
        val navigateToCredentials: (View) -> Unit = {
            launchAdminSettings()
        }
        bannerView.setOnClickListener(navigateToCredentials)
        clickTarget?.setOnClickListener(navigateToCredentials)
        binding.mainHolder.addView(bannerView, 0) // Add at top
        credentialsBannerView = bannerView
    }

    private fun hideCredentialsBanner() {
        android.util.Log.d("MainActivity", "Hiding credentials status banner")
        credentialsBannerView?.let { view ->
            binding.mainHolder.removeView(view)
            credentialsBannerView = null
        }
    }
}
