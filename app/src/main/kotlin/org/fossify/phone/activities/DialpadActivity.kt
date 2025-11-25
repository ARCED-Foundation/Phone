package org.fossify.phone.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony.Sms.Intents.SECRET_CODE_ACTION
import android.telephony.TelephonyManager
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.checkAppSideloading
import org.fossify.commons.extensions.getColorStateList
import org.fossify.commons.extensions.getColoredDrawableWithColor
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.isDefaultDialer
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.extensions.onTextChangeListener
import org.fossify.commons.extensions.performHapticFeedback
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.value
import org.fossify.commons.extensions.viewBinding
import org.fossify.phone.extensions.validateOdkIntent
import org.fossify.phone.extensions.saveLastOdkValue
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.KeypadHelper
import org.fossify.commons.helpers.LOWER_ALPHA_INT
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.REQUEST_CODE_SET_DEFAULT_DIALER
import org.fossify.commons.helpers.isOreoPlus
import org.fossify.commons.models.contacts.Contact
import org.fossify.phone.R
import org.fossify.phone.adapters.ContactsAdapter
import org.fossify.phone.databinding.ActivityDialpadBinding
import org.fossify.phone.extensions.addCharacter
import org.fossify.phone.extensions.areMultipleSIMsAvailable
import org.fossify.phone.extensions.boundingBox
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.disableKeyboard
import org.fossify.phone.extensions.getKeyEvent
import org.fossify.phone.extensions.setupWithContacts
import org.fossify.phone.extensions.startAddContactIntent
import org.fossify.phone.extensions.startCallWithConfirmationCheck
import org.fossify.phone.extensions.startContactDetailsIntent
import org.fossify.phone.helpers.CallManager
import org.fossify.phone.helpers.CallManagerListener
import org.fossify.phone.helpers.DIALPAD_TONE_LENGTH_MS
import org.fossify.phone.helpers.RecentsHelper
import org.fossify.phone.helpers.ToneGeneratorHelper
import org.fossify.phone.models.SpeedDial
import org.fossify.phone.models.OdkCallRecord
import org.fossify.phone.models.OdkCallTrackingInfo
import org.fossify.phone.models.ODKSession
import org.fossify.phone.models.CallDirection
import org.fossify.phone.models.ConcatenatedValue
import android.telecom.Call
import org.fossify.phone.models.AudioRoute
import android.content.BroadcastReceiver
import android.content.IntentFilter
import kotlinx.serialization.json.Json
import java.util.Locale
import kotlin.math.roundToInt

class DialpadActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityDialpadBinding::inflate)
    private val savedStateHandle by lazy { SavedStateHandle() }

    // ODK session state using CallManager
    private var odkSession: ODKSession? = null
    private var odkCallManagerListener: CallManagerListener? = null
    private var isOdkSession = false
    private var firstOdkConnect = false
    private var odkReturnReceiver: BroadcastReceiver? = null

    private var allContacts = ArrayList<Contact>()
    private var speedDialValues = ArrayList<SpeedDial>()
    private var privateCursor: Cursor? = null
    private var toneGeneratorHelper: ToneGeneratorHelper? = null
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val pressedKeys = mutableSetOf<Char>()

    // ODK data classes removed - using models from org.fossify.phone.models package

    private var hasRussianLocale = false
    private val russianCharsMap by lazy {
        hashMapOf(
            'а' to 2, 'б' to 2, 'в' to 2, 'г' to 2,
            'д' to 3, 'е' to 3, 'ё' to 3, 'ж' to 3, 'з' to 3,
            'и' to 4, 'й' to 4, 'к' to 4, 'л' to 4,
            'м' to 5, 'н' to 5, 'о' to 5, 'п' to 5,
            'р' to 6, 'с' to 6, 'т' to 6, 'у' to 6,
            'ф' to 7, 'х' to 7, 'ц' to 7, 'ч' to 7,
            'ш' to 8, 'щ' to 8, 'ъ' to 8, 'ы' to 8,
            'ь' to 9, 'э' to 9, 'ю' to 9, 'я' to 9
        )
    }

    @Suppress("LongMethod")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        hasRussianLocale = Locale.getDefault().language == "ru"

        // Handle ODK intent
        handleOdkIntent()

        // Register ODK return broadcast receiver
        odkReturnReceiver = OdkReturnReceiver()
        val filter = IntentFilter("org.fossify.phone.ODK_RETURN_FULL")
        registerReceiver(odkReturnReceiver, filter)

        // Register CallManager listener for ODK tracking
        val listener = object : CallManagerListener {
            override fun onStateChanged() {}
            override fun onAudioStateChanged(audioState: AudioRoute) {}
            override fun onPrimaryCallChanged(call: Call) {}
            override fun onCallStarted(number: String, isOutgoing: Boolean) {
                // Legacy method - enhanced version with Call object is preferred
                // For now, we'll create a dummy call object and use the enhanced method
                android.util.Log.d("ODK_INTEGRATION", "Legacy onCallStarted called: number=$number, isOutgoing=$isOutgoing")
                // TODO: Consider removing this legacy method implementation once CallManager is updated
            }
            override fun onCallActive(number: String, isOutgoing: Boolean) {
                // Legacy method - enhanced version with Call object is preferred
                // For now, we'll log and ignore since we can't get the Call object
                android.util.Log.d("ODK_INTEGRATION", "Legacy onCallActive called: number=$number, isOutgoing=$isOutgoing")
            }
            override fun onCallEnded() {
                // ODK call completion handled centrally by CallManager
            }
            // Enhanced methods that use Call objects for reliable tracking
            override fun onCallStarted(call: Call, number: String, isOutgoing: Boolean) {
                // ODK tracking handled centrally by CallManager
            }
            override fun onCallActive(call: Call, number: String, isOutgoing: Boolean) {
                if (isOdkSession && !firstOdkConnect) {
                    firstOdkConnect = true
                    android.util.Log.d("ODK_INTEGRATION", "First ODK call active - auto-return partial")
                    returnToOdk(isPartial = true)
                }
            }
        }
        odkCallManagerListener = listener
        CallManager.addListener(listener)

        binding.apply {
            setupEdgeToEdge(
                padBottomImeAndSystem = listOf(dialpadList, dialpadHolder)
            )
            setupMaterialScrollListener(binding.dialpadList, binding.dialpadAppbar)
        }

        if (checkAppSideloading()) {
            return
        }

        binding.dialpadWrapper.apply {
            if (config.hideDialpadNumbers) {
                dialpad1Holder.isVisible = false
                dialpad2Holder.isVisible = false
                dialpad3Holder.isVisible = false
                dialpad4Holder.isVisible = false
                dialpad5Holder.isVisible = false
                dialpad6Holder.isVisible = false
                dialpad7Holder.isVisible = false
                dialpad8Holder.isVisible = false
                dialpad9Holder.isVisible = false
                dialpadPlusHolder.isVisible = true
                dialpad0Holder.visibility = View.INVISIBLE
            }

            arrayOf(
                dialpad0Holder,
                dialpad1Holder,
                dialpad2Holder,
                dialpad3Holder,
                dialpad4Holder,
                dialpad5Holder,
                dialpad6Holder,
                dialpad7Holder,
                dialpad8Holder,
                dialpad9Holder,
                dialpadPlusHolder,
                dialpadAsteriskHolder,
                dialpadHashtagHolder
            ).forEach {
                it.background =
                    ResourcesCompat.getDrawable(resources, R.drawable.pill_background, theme)
                it.background?.alpha = LOWER_ALPHA_INT
            }
        }

        setupOptionsMenu()
        speedDialValues = config.getSpeedDialValues()
        privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)

        toneGeneratorHelper = ToneGeneratorHelper(this, DIALPAD_TONE_LENGTH_MS)

        binding.dialpadWrapper.apply {
            if (hasRussianLocale) {
                dialpad2Letters.append("\nАБВГ")
                dialpad3Letters.append("\nДЕЁЖЗ")
                dialpad4Letters.append("\nИЙКЛ")
                dialpad5Letters.append("\nМНОП")
                dialpad6Letters.append("\nРСТУ")
                dialpad7Letters.append("\nФХЦЧ")
                dialpad8Letters.append("\nШЩЪЫ")
                dialpad9Letters.append("\nЬЭЮЯ")

                val fontSize = resources.getDimension(R.dimen.small_text_size)
                arrayOf(
                    dialpad2Letters,
                    dialpad3Letters,
                    dialpad4Letters,
                    dialpad5Letters,
                    dialpad6Letters,
                    dialpad7Letters,
                    dialpad8Letters,
                    dialpad9Letters
                ).forEach {
                    it.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
                }
            }

            setupCharClick(dialpad1Holder, '1')
            setupCharClick(dialpad2Holder, '2')
            setupCharClick(dialpad3Holder, '3')
            setupCharClick(dialpad4Holder, '4')
            setupCharClick(dialpad5Holder, '5')
            setupCharClick(dialpad6Holder, '6')
            setupCharClick(dialpad7Holder, '7')
            setupCharClick(dialpad8Holder, '8')
            setupCharClick(dialpad9Holder, '9')
            setupCharClick(dialpad0Holder, '0')
            setupCharClick(dialpadPlusHolder, '+', longClickable = false)
            setupCharClick(dialpadAsteriskHolder, '*', longClickable = false)
            setupCharClick(dialpadHashtagHolder, '#', longClickable = false)
        }

        binding.apply {
            dialpadClearChar.setOnClickListener { clearChar(it) }
            dialpadClearChar.setOnLongClickListener { clearInput(); true }
            dialpadCallButton.setOnClickListener { initCall(dialpadInput.value) }
            dialpadCallButton.setOnLongClickListener { initCallWithSimSelector() }
            dialpadInput.onTextChangeListener { dialpadValueChanged(it) }
            dialpadInput.requestFocus()
            dialpadInput.disableKeyboard()

        }

        ContactsHelper(this).getContacts(showOnlyContactsWithNumbers = true) { allContacts ->
            gotContacts(allContacts)
        }

        val properPrimaryColor = getProperPrimaryColor()
        binding.apply {
            val callIcon = resources.getColoredDrawableWithColor(
                drawableId = R.drawable.ic_phone_vector,
                color = properPrimaryColor.getContrastColor()
            )
            dialpadCallButton.setImageDrawable(callIcon)
            dialpadCallButton.background.applyColorFilter(properPrimaryColor)

            letterFastscroller.textColor = getProperTextColor().getColorStateList()
            letterFastscroller.pressedTextColor = properPrimaryColor
            letterFastscrollerThumb.setupWithFastScroller(letterFastscroller)
            letterFastscrollerThumb.textColor = properPrimaryColor.getContrastColor()
            letterFastscrollerThumb.thumbColor = properPrimaryColor.getColorStateList()
        }
    }

    override fun onResume() {
        super.onResume()
        updateTextColors(binding.dialpadHolder)
        binding.dialpadClearChar.applyColorFilter(getProperTextColor())
        setupTopAppBar(binding.dialpadAppbar, NavigationIcon.Arrow)
    }

    private fun setupOptionsMenu() {
        binding.dialpadToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.add_number_to_contact -> addNumberToContact()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun checkDialIntent(): Boolean {
        return if (
            (intent.action == Intent.ACTION_DIAL || intent.action == Intent.ACTION_VIEW)
            && intent.data != null && intent.dataString?.contains("tel:") == true
        ) {
            val number = Uri.decode(intent.dataString).substringAfter("tel:")
            binding.dialpadInput.setText(number)
            binding.dialpadInput.setSelection(number.length)
            true
        } else {
            false
        }
    }

    private fun addNumberToContact() {
        startAddContactIntent(binding.dialpadInput.value)
    }

    private fun dialpadPressed(char: Char, view: View?) {
        binding.dialpadInput.addCharacter(char)
        maybePerformDialpadHapticFeedback(view)
    }

    private fun clearChar(view: View) {
        binding.dialpadInput.dispatchKeyEvent(binding.dialpadInput.getKeyEvent(KeyEvent.KEYCODE_DEL))
        maybePerformDialpadHapticFeedback(view)
    }

    private fun clearInput() {
        binding.dialpadInput.setText("")
    }

    private fun clearInputWithDelay() {
        lifecycleScope.launch {
            delay(1000)
            clearInput()
        }
    }

    private fun gotContacts(newContacts: ArrayList<Contact>) {
        allContacts = newContacts

        val privateContacts = MyContactsContentProvider.getContacts(this, privateCursor)
        if (privateContacts.isNotEmpty()) {
            allContacts.addAll(privateContacts)
            allContacts.sort()
        }

        runOnUiThread {
            if (!checkDialIntent() && binding.dialpadInput.value.isEmpty()) {
                dialpadValueChanged("")
            }
        }
    }

    private fun dialpadValueChanged(text: String) {
        val len = text.length
        if (len > 8 && text.startsWith("*#*#") && text.endsWith("#*#*")) {
            val secretCode = text.substring(4, text.length - 4)
            if (isOreoPlus()) {
                if (isDefaultDialer()) {
                    getSystemService(TelephonyManager::class.java)?.sendDialerSpecialCode(secretCode)
                } else {
                    launchSetDefaultDialerIntent()
                }
            } else {
                val intent =
                    Intent(SECRET_CODE_ACTION, "android_secret_code://$secretCode".toUri())
                sendBroadcast(intent)
            }
            return
        }

        (binding.dialpadList.adapter as? ContactsAdapter)?.finishActMode()

        val filtered = allContacts.filter { contact ->
            var convertedName = KeypadHelper.convertKeypadLettersToDigits(
                contact.name.normalizeString()
            ).filterNot { it.isWhitespace() }

            if (hasRussianLocale) {
                var currConvertedName = ""
                convertedName.lowercase(Locale.getDefault()).forEach { char ->
                    val convertedChar = russianCharsMap.getOrElse(char) { char }
                    currConvertedName += convertedChar
                }
                convertedName = currConvertedName
            }

            contact.doesContainPhoneNumber(text) || (convertedName.contains(text, true))
        }.sortedWith(compareBy {
            !it.doesContainPhoneNumber(text)
        }).toMutableList() as ArrayList<Contact>

        binding.letterFastscroller.setupWithContacts(binding.dialpadList, filtered)

        ContactsAdapter(
            activity = this,
            contacts = filtered,
            recyclerView = binding.dialpadList,
            highlightText = text,
            itemClick = {
                startCallWithConfirmationCheck(it as Contact)
                clearInputWithDelay()
            },
            profileIconClick = {
                startContactDetailsIntent(it as Contact)
            }).apply {
            binding.dialpadList.adapter = this
        }

        binding.dialpadPlaceholder.beVisibleIf(filtered.isEmpty())
        binding.dialpadList.beVisibleIf(filtered.isNotEmpty())
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == REQUEST_CODE_SET_DEFAULT_DIALER && isDefaultDialer()) {
            dialpadValueChanged(binding.dialpadInput.value)
        }
    }

    private fun initCall(number: String = binding.dialpadInput.value, name: String? = null) {
        if (number.isNotEmpty()) {
            startCallWithConfirmationCheck(number, name ?: number)
            clearInputWithDelay()
        } else {
            RecentsHelper(this).getRecentCalls(queryLimit = 1) {
                val mostRecentNumber = it.firstOrNull()?.phoneNumber
                if (!mostRecentNumber.isNullOrEmpty()) {
                    runOnUiThread {
                        binding.dialpadInput.setText(mostRecentNumber)
                    }
                }
            }
        }
    }

    private fun initCallWithSimSelector(): Boolean {
        val number = binding.dialpadInput.value
        return if (areMultipleSIMsAvailable() && number.isNotEmpty()) {
            startCallWithConfirmationCheck(
                recipient = number,
                name = number,
                forceSimSelector = true
            )
            true
        } else {
            false
        }
    }

    private fun speedDial(id: Int): Boolean {
        if (binding.dialpadInput.value.length == 1) {
            val speedDial = speedDialValues.firstOrNull { it.id == id }
            if (speedDial?.isValid() == true) {
                initCall(speedDial.number, speedDial.getName(this))
                return true
            }
        }
        return false
    }

    private fun startDialpadTone(char: Char) {
        if (config.dialpadBeeps) {
            pressedKeys.add(char)
            toneGeneratorHelper?.startTone(char)
        }
    }

    private fun stopDialpadTone(char: Char) {
        if (config.dialpadBeeps) {
            if (!pressedKeys.remove(char)) return
            if (pressedKeys.isEmpty()) {
                toneGeneratorHelper?.stopTone()
            } else {
                startDialpadTone(pressedKeys.last())
            }
        }
    }

    private fun maybePerformDialpadHapticFeedback(view: View?) {
        if (config.dialpadVibration) {
            view?.performHapticFeedback()
        }
    }

    private fun performLongClick(view: View, char: Char) {
        if (char == '0') {
            clearChar(view)
            dialpadPressed('+', view)
        } else {
            val result = speedDial(char.digitToInt())
            if (result) {
                stopDialpadTone(char)
                clearChar(view)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupCharClick(view: View, char: Char, longClickable: Boolean = true) {
        view.isClickable = true
        view.isLongClickable = true
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dialpadPressed(char, view)
                    startDialpadTone(char)
                    if (longClickable) {
                        longPressHandler.removeCallbacksAndMessages(null)
                        longPressHandler.postDelayed({
                            performLongClick(view, char)
                        }, longPressTimeout)
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopDialpadTone(char)
                    if (longClickable) {
                        longPressHandler.removeCallbacksAndMessages(null)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    val viewContainsTouchEvent = if (event.rawX.isNaN() || event.rawY.isNaN()) {
                        false
                    } else {
                        view.boundingBox.contains(event.rawX.roundToInt(), event.rawY.roundToInt())
                    }

                    if (!viewContainsTouchEvent) {
                        stopDialpadTone(char)
                        if (longClickable) {
                            longPressHandler.removeCallbacksAndMessages(null)
                        }
                    }
                }
            }
            false
        }
    }

    // ODK Integration Methods
    private fun handleOdkIntent() {
        android.util.Log.d("ODK_INTEGRATION", "handleOdkIntent called with intent: $intent")

        // Debug: Log all intent details
        intent?.let { intent ->
            android.util.Log.d("ODK_INTEGRATION", "Intent action: ${intent.action}")
            android.util.Log.d("ODK_INTEGRATION", "Intent data: ${intent.data}")
            android.util.Log.d("ODK_INTEGRATION", "Intent categories: ${intent.categories}")
            intent.extras?.keySet()?.forEach { key ->
                val value = intent.extras?.get(key)
                android.util.Log.d("ODK_INTEGRATION", "Extra '$key': $value (type: ${value?.javaClass?.simpleName})")
            }
            val intentValue = intent.getStringExtra("value")
            android.util.Log.d("ODK_INTEGRATION", "Intent 'value' extra: '$intentValue'")
        }

        intent?.validateOdkIntent()?.let { validationResult ->
            android.util.Log.d("ODK_INTEGRATION", "Validation result: $validationResult")
            android.util.Log.d("ODK_INTEGRATION", "Is valid: ${validationResult.isValid}")
            android.util.Log.d("ODK_INTEGRATION", "Phone number: ${validationResult.phoneNumber}")
            android.util.Log.d("ODK_INTEGRATION", "Existing value: ${validationResult.existingValue}")
            android.util.Log.d("ODK_INTEGRATION", "Variant: ${validationResult.variant}")

            if (validationResult.isValid) {
                // Set ODK session flag
                isOdkSession = true
                android.util.Log.d("ODK_INTEGRATION", "ODK session set to true")

                // Initialize ODK session with CallManager (fallback if no value)
                val fieldId = intent.getStringExtra("odk_field_id")
                val callingPackage = callingActivity?.packageName
                val autoReturnDisconnect = intent.getBooleanExtra("auto_return_disconnect", true)

                android.util.Log.d("ODK_INTEGRATION", "ODK extras - fieldId: \$fieldId, callingPackage: \$callingPackage, autoReturnDisconnect: \$autoReturnDisconnect")

                CallManager.initializeOdkSession(
                    context = this,
                    phoneNumber = validationResult.phoneNumber,
                    existingValue = validationResult.existingValue,
                    variant = validationResult.variant,
                    fieldId = fieldId,
                    callingPackage = callingPackage,
                    autoReturnDisconnect = autoReturnDisconnect
                )
                android.util.Log.d("ODK_INTEGRATION", "ODK session initialized with CallManager")

                // Auto-populate dialpad with the phone number
                validationResult.phoneNumber?.let { phoneNumber ->
                    binding.dialpadInput.setText(phoneNumber)
                    binding.dialpadInput.setSelection(phoneNumber.length)
                    android.util.Log.d("ODK_INTEGRATION", "Dialpad populated with phone number: $phoneNumber")
                }

                // Show ODK mode indicator
                showOdkModeUI()
                android.util.Log.d("ODK_INTEGRATION", "ODK mode UI shown")
            } else {
                android.util.Log.d("ODK_INTEGRATION", "ODK intent validation failed: ${validationResult.errors}")
            }
        } ?: run {
            android.util.Log.d("ODK_INTEGRATION", "No intent or validation result returned null")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOdkIntent()
    }

    private fun showOdkModeUI() {
        binding.odkModeIndicator?.visibility = View.VISIBLE
        binding.finishOdkButton?.visibility = View.VISIBLE
        binding.finishOdkButton?.setOnClickListener {
            showFinishConfirmation()
        }
    }

    private fun showFinishConfirmation() {
        val session = CallManager.getOdkSession()
        val callCount = session?.callRecords?.size ?: 0
        val preview = CallManager.getConcatenatedOdkValue().take(100) + if (CallManager.getConcatenatedOdkValue().length > 100) "..." else ""

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Return to ODK Collect?")
            .setMessage("$callCount call(s) logged.\\nPreview: $preview")
            .setPositiveButton("Return Data") { _, _ -> returnToOdk() }
            .setNegativeButton("Continue Calls", null)
            .show()
    }

    private fun returnToOdk() {
        if (!CallManager.isOdkSessionActive()) return

        try {
            // End ODK session and get concatenated value
            val concatenatedValue = CallManager.getConcatenatedOdkValue()
            CallManager.endOdkSession()

            // Reset ODK session flag
            isOdkSession = false

            // Persist full concatenated value for next session fallback
            saveLastOdkValue(concatenatedValue)

// Create return intent with String extra named "value" (ODK standard)
            val returnIntent = Intent().apply {
                putExtra("value", concatenatedValue)
            }

            android.util.Log.d("ODK_INTEGRATION", "Returning data to ODK: $concatenatedValue (saved for fallback)")

            setResult(RESULT_OK, returnIntent)
            finish()

        } catch (e: Exception) {
            android.util.Log.e("ODK_INTEGRATION", "Error returning data to ODK", e)
            Toast.makeText(this, "Error: Cannot save call data", Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    override fun onBackPressed() {
        if (isOdkSession) {
            showFinishConfirmation()
        } else {
            super.onBackPressed()
        }
    }

    private inner class OdkReturnReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "org.fossify.phone.ODK_RETURN_FULL") {
                android.util.Log.d("ODK_INTEGRATION", "Received ODK return broadcast")
                returnToOdk()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up ODK CallManager listener
        odkCallManagerListener?.let { listener ->
            CallManager.removeListener(listener)
            odkCallManagerListener = null
        }
        // Unregister receiver if registered
        try {
            unregisterReceiver(odkReturnReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered
        }
    }


    // Session state persistence methods
    private fun saveOdkSessionState() {
        odkSession?.let { session ->
            savedStateHandle.set("odk_session_is_active", session.isActive)
            savedStateHandle.set("odk_session_phone_number", session.phoneNumber)
            savedStateHandle.set("odk_session_existing_value", session.existingValue)
            savedStateHandle.set("odk_session_start_time", session.sessionStartTime)
            savedStateHandle.set("odk_session_variant", session.sessionVariant)
        }
    }

    private fun restoreOdkSessionState(): Boolean {
        val isActive = savedStateHandle.get<Boolean>("odk_session_is_active") ?: false
        if (isActive) {
            odkSession = ODKSession(
                phoneNumber = savedStateHandle.get<String>("odk_session_phone_number"),
                existingValue = savedStateHandle.get<String>("odk_session_existing_value"),
                sessionStartTime = savedStateHandle.get<Long>("odk_session_start_time") ?: System.currentTimeMillis(),
                sessionVariant = savedStateHandle.get<String>("odk_session_variant"),
                isActive = isActive,
                callRecords = emptyList<OdkCallRecord>(),
                activeCalls = emptyMap<String, OdkCallTrackingInfo>()
            )

            // Restore dialpad text if phone number exists
            odkSession?.phoneNumber?.let { phoneNumber ->
                binding.dialpadInput.setText(phoneNumber)
                binding.dialpadInput.setSelection(phoneNumber.length)
            }

            showOdkModeUI()
            return true
        }
        return false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        saveOdkSessionState()
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        restoreOdkSessionState()
    }


}
