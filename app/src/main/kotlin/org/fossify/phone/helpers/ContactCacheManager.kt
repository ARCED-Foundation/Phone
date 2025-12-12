package org.fossify.phone.helpers

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.models.contacts.Contact
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages caching of contact data to avoid duplicate loading across the app
 * Provides background loading and proper cache invalidation
 */
class ContactCacheManager private constructor(private val context: Context) {

    private val cachedContacts = mutableListOf<Contact>()
    private val phoneToContactMap = ConcurrentHashMap<String, Contact>()
    private val normalizedPhoneToContactMap = ConcurrentHashMap<String, Contact>()
    private val cacheLock = Mutex()
    private val isLoading = AtomicBoolean(false)

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Get cached contacts or load them if not available
     */
    fun getContacts(forceReload: Boolean = false): List<Contact> {
        if (!forceReload && cachedContacts.isNotEmpty()) {
            return cachedContacts.toList()
        }

        return loadContactsInternal()
    }

    /**
     * Get contact by phone number with cache lookup
     */
    fun getContactByPhoneNumber(phoneNumber: String): Contact? {
        // Check regular phone number map first
        phoneToContactMap[phoneNumber]?.let { return it }

        // Check normalized phone number map
        // TODO: Add normalized number support when extension functions are available

        // If not in cache, return null (fallback will handle loading)
        return null
    }

    /**
     * Preload contacts in background
     */
    fun preloadContacts() {
        if (isLoading.get()) return

        coroutineScope.launch(Dispatchers.IO) {
            ensureBackgroundThread {
                if (cachedContacts.isEmpty()) {
                    loadContactsInternal()
                }
            }
        }
    }

    /**
     * Clear cache and force reload
     */
    fun clearCache() {
        cachedContacts.clear()
        phoneToContactMap.clear()
        normalizedPhoneToContactMap.clear()
    }

    /**
     * Get cached phone number to contact mapping for quick lookups
     */
    fun getPhoneNumberMapping(): Map<String, Contact> = phoneToContactMap.toMap()

    /**
     * Get cached normalized phone number to contact mapping
     */
    fun getNormalizedPhoneNumberMapping(): Map<String, Contact> = normalizedPhoneToContactMap.toMap()

    private fun loadContactsInternal(): List<Contact> {
        if (isLoading.get()) return cachedContacts.toList()

        isLoading.set(true)

        try {
            val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)

            // Load contacts from provider
            val contacts = mutableListOf<Contact>()
            ContactsHelper(context).getContacts(getAll = true, showOnlyContactsWithNumbers = true) { loadedContacts ->
                contacts.addAll(loadedContacts)
            }

            // Add private contacts if not ignored
            if (SMT_PRIVATE !in context.baseConfig.ignoredContactSources) {
                val privateContacts = MyContactsContentProvider.getContacts(context, privateCursor)
                if (privateContacts.isNotEmpty()) {
                    contacts.addAll(privateContacts)
                }
            }

            // Sort contacts
            contacts.sort()

            // Update cache
            cachedContacts.clear()
            cachedContacts.addAll(contacts)

            // Clear and rebuild phone number mappings
            phoneToContactMap.clear()
            // normalizedPhoneToContactMap.clear() - TODO: Re-enable when normalized numbers supported

            contacts.forEach { contact ->
                contact.phoneNumbers.forEach { phoneNumber ->
                    // Map phone number to contact
                    phoneToContactMap[phoneNumber.value] = contact

                    // TODO: Map normalized phone number to contact when extension functions are available
                }
            }

            return contacts

        } finally {
            isLoading.set(false)
        }
    }

    /**
     * Update a specific contact in cache
     */
    fun updateContact(updatedContact: Contact) {
        val index = cachedContacts.indexOfFirst { it.contactId == updatedContact.contactId }
        if (index != -1) {
            cachedContacts[index] = updatedContact
        } else {
            cachedContacts.add(updatedContact)
        }

        // Rebuild mappings
        rebuildPhoneNumberMappings()
    }

    /**
     * Remove a contact from cache
     */
    fun removeContact(contactId: Int) {
        cachedContacts.removeAll { it.contactId == contactId }
        rebuildPhoneNumberMappings()
    }

    private fun rebuildPhoneNumberMappings() {
        phoneToContactMap.clear()
        // normalizedPhoneToContactMap.clear() - TODO: Re-enable when normalized numbers supported

        cachedContacts.forEach { contact ->
            contact.phoneNumbers.forEach { phoneNumber ->
                phoneToContactMap[phoneNumber.value] = contact

                // TODO: Map normalized phone number to contact when extension functions are available
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ContactCacheManager? = null

        fun getInstance(context: Context): ContactCacheManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ContactCacheManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun clearInstance() {
            INSTANCE = null
        }
    }
}