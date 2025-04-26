package com.asforce.asforcetkf2.webview

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.asforce.asforcetkf2.R

/**
 * Helper class to search all comboboxes in a WebView for matching items
 * Supports case-insensitive search and Turkish character normalization
 */
class ComboboxSearchHelper(private val webView: TabWebView) {

    /**
     * Search all comboboxes in the WebView for items matching the search text
     * 
     * @param searchText Text to search for
     * @param onItemFound Callback called when a matching item is found and selected
     * @param onSearchComplete Callback called when the search is complete
     * @param onNoResults Callback called when no matching items are found
     */
    fun searchComboboxes(
        searchText: String,
        onItemFound: ((comboboxName: String, itemText: String) -> Unit)? = null,
        onSearchComplete: (() -> Unit)? = null,
        onNoResults: (() -> Unit)? = null
    ) {
        if (searchText.isBlank()) {
            onNoResults?.invoke()
            return
        }
        
        // Normalize the search text for case-insensitive, accent-insensitive comparison
        val normalizedSearchText = normalizeText(searchText)
        
        // Script to find and check all comboboxes in the page
        val searchScript = """
        (function() {
            // Normalize text for comparison - handles Turkish characters and case insensitivity
            function normalizeText(text) {
                if (!text) return '';
                
                // First convert to lowercase
                var normalizedText = text.toLowerCase();
                
                // Replace all Turkish characters with Latin equivalents - both uppercase and lowercase versions
                var replacements = {
                    'ğ': 'g', 'Ğ': 'g',
                    'ü': 'u', 'Ü': 'u',
                    'ş': 's', 'Ş': 's',
                    'ı': 'i', 'I': 'i',
                    'i': 'i', 'İ': 'i',
                    'ö': 'o', 'Ö': 'o',
                    'ç': 'c', 'Ç': 'c'
                };
                
                // Loop through all replacements
                for (var original in replacements) {
                    // Create a global regex for the character
                    var regex = new RegExp(original, 'g');
                    normalizedText = normalizedText.replace(regex, replacements[original]);
                }
                
                // Additional cleanup: remove all accents and diacritical marks
                normalizedText = normalizedText.normalize("NFD").replace(/[\u0300-\u036f]/g, "");
                
                return normalizedText;
            }
            
            // Result object to store search results
            const result = {
                found: false,
                totalComboboxes: 0,
                searchedComboboxes: 0,
                matchedComboboxes: 0,
                details: []
            };
            
            try {
                // Find all standard select elements (comboboxes) in the document
                const selectElements = document.querySelectorAll('select');
                
                // Find additional dropdown elements that might be custom dropdowns
                const bootstrapSelects = document.querySelectorAll('.bootstrap-select .dropdown-toggle');
                const select2Elements = document.querySelectorAll('.select2-selection');
                const customDropdowns = document.querySelectorAll('[role="combobox"], [role="listbox"], .dropdown-toggle');
                
                // Total count of all types of comboboxes
                result.totalComboboxes = selectElements.length + bootstrapSelects.length + select2Elements.length + customDropdowns.length;
                
                if (result.totalComboboxes === 0) {
                    console.log('No comboboxes found in the page');
                    return result;
                }
                
                // Log the discovered dropdowns
                console.log('Found ' + selectElements.length + ' standard selects, ' + 
                           bootstrapSelects.length + ' bootstrap-selects, ' + 
                           select2Elements.length + ' select2 elements, and ' + 
                           customDropdowns.length + ' custom dropdowns');
                
                // Track number of matches found
                let matchesFound = 0;
                
                // Process each select element
                for (let i = 0; i < selectElements.length; i++) {
                    const select = selectElements[i];
                    const options = select.options;
                    const comboboxName = select.name || select.id || ('combobox_' + i);
                    
                    // Log for debugging
                    console.log('Searching combobox: ' + comboboxName + ' with ' + options.length + ' options');
                    
                    // Skip hidden or disabled select elements
                    if (select.disabled || !select.offsetParent) {
                        continue;
                    }
                    
                    result.searchedComboboxes++;
                    
                    // Before searching, log what we're looking for
                    console.log('Searching for: ' + '${normalizedSearchText}');
                    
                    // Search for a match in options
                    let matchFound = false;
                    let matchIndex = -1;
                    
                    for (let j = 0; j < options.length; j++) {
                        const option = options[j];
                        const optionText = option.text || option.innerHTML || '';
                        
                        // Normalize the option text for comparison
                        const normalizedOptionText = normalizeText(optionText);
                        
                        // Debug log to see actual comparison
                        console.log('Comparing: "' + optionText + '" normalized as "' + normalizedOptionText + '"');
                        
                        // First try direct equality for better matching
                        if (normalizedOptionText === '${normalizedSearchText}') {
                            console.log('EXACT MATCH FOUND!');
                            matchFound = true;
                            matchIndex = j;
                            matchesFound++;
                            
                            // Store match details
                            result.details.push({
                                comboboxName: comboboxName,
                                matchedItem: optionText,
                                originalIndex: j,
                                matchType: 'exact'
                            });
                            
                            // Try to select this option
                            try {
                                // Set the value directly
                                select.selectedIndex = j;
                                
                                // Trigger change event
                                const event = new Event('change', { bubbles: true });
                                select.dispatchEvent(event);
                                
                                // Update select picker if Bootstrap is used
                                if (typeof $ !== 'undefined' && $('.selectpicker').length > 0) {
                                    $(select).selectpicker('refresh');
                                }
                                
                                // Scroll to the element to make it visible
                                select.scrollIntoView({ behavior: 'smooth', block: 'center' });
                                
                                // Apply the selection but force dropdown to close
                                setTimeout(function() {
                                    // Force close all dropdowns
                                    // 1. Blur the select
                                    select.blur();
                                    // 2. Click outside
                                    document.body.click();
                                    // 3. Escape key to close dropdowns
                                    document.dispatchEvent(new KeyboardEvent('keydown', {
                                        key: 'Escape',
                                        code: 'Escape',
                                        keyCode: 27,
                                        which: 27,
                                        bubbles: true
                                    }));
                                }, 200);
                            } catch (e) {
                                console.error('Error selecting option:', e);
                            }
                            
                            break; // Stop after first match in this combobox
                        }
                        // Then try includes() for partial matching
                        else if (normalizedOptionText.includes('${normalizedSearchText}')) {
                            matchFound = true;
                            matchIndex = j;
                            matchesFound++;
                            
                            // Store match details
                            result.details.push({
                                comboboxName: comboboxName,
                                matchedItem: optionText,
                                originalIndex: j
                            });
                            
                            // Try to select this option
                            try {
                                // Set the value directly
                                select.selectedIndex = j;
                                
                                // Trigger change event
                                const event = new Event('change', { bubbles: true });
                                select.dispatchEvent(event);
                                
                                // Update select picker if Bootstrap is used
                                if (typeof $ !== 'undefined' && $('.selectpicker').length > 0) {
                                    $(select).selectpicker('refresh');
                                }
                                
                                // Scroll to the element to make it visible
                                select.scrollIntoView({ behavior: 'smooth', block: 'center' });
                                
                                // Apply the selection but force dropdown to close
                                setTimeout(function() {
                                    // Force close all dropdowns
                                    // 1. Blur the select
                                    select.blur();
                                    // 2. Click outside
                                    document.body.click();
                                    // 3. Escape key to close dropdowns
                                    document.dispatchEvent(new KeyboardEvent('keydown', {
                                        key: 'Escape',
                                        code: 'Escape',
                                        keyCode: 27,
                                        which: 27,
                                        bubbles: true
                                    }));
                                }, 200);
                            } catch (e) {
                                console.error('Error selecting option:', e);
                            }
                            
                            break; // Stop after first match in this combobox
                        }
                    }
                    
                    if (matchFound) {
                        result.matchedComboboxes++;
                    }
                }
                
                // Process Bootstrap select elements
                for (let i = 0; i < bootstrapSelects.length; i++) {
                    const dropdownToggle = bootstrapSelects[i];
                    const container = dropdownToggle.closest('.bootstrap-select');
                    if (!container || !container.offsetParent) continue;
                    
                    result.searchedComboboxes++;
                    
                    // Find the hidden select element that is controlled by this bootstrap-select
                    const selectId = container.getAttribute('data-id') || container.getAttribute('id');
                    const selectElement = selectId ? document.getElementById(selectId) : 
                        container.querySelector('select') || container.previousElementSibling;
                        
                    if (!selectElement) continue;
                    
                    // Get the combobox name
                    const comboboxName = selectElement.name || selectElement.id || ('bootstrap_select_' + i);
                    
                    // Get options from the select element
                    const options = selectElement.options;
                    let matchFound = false;
                    let matchIndex = -1;
                    
                    for (let j = 0; j < options.length; j++) {
                        const option = options[j];
                        const optionText = option.text || option.innerHTML || '';
                        
                        // Normalize the option text for comparison
                        const normalizedOptionText = normalizeText(optionText);
                        
                        // Check if the option text contains the search text
                        if (normalizedOptionText.includes('${normalizedSearchText}')) {
                            matchFound = true;
                            matchIndex = j;
                            matchesFound++;
                            
                            // Store match details
                            result.details.push({
                                comboboxName: comboboxName,
                                matchedItem: optionText,
                                originalIndex: j
                            });
                            
                            // Try to select this option
                            try {
                                // Set the value directly on the hidden select
                                selectElement.selectedIndex = j;
                                
                                // Trigger change event
                                const event = new Event('change', { bubbles: true });
                                selectElement.dispatchEvent(event);
                                
                                // Update the Bootstrap selectpicker
                                if (typeof $ !== 'undefined') {
                                $(selectElement).selectpicker('val', option.value);
                                $(selectElement).selectpicker('refresh');
                                
                                // Instead of opening and closing the dropdown, just force it closed
                                setTimeout(function() {
                                    // Force dropdown to close
                                // 1. Blur
                                    selectElement.blur();
                                        // 2. Click outside
                                            document.body.click();
                                            // 3. Escape key event
                                            document.dispatchEvent(new KeyboardEvent('keydown', {
                                                key: 'Escape',
                                                code: 'Escape',
                                                keyCode: 27,
                                                which: 27,
                                                bubbles: true
                                            }));
                                            // 4. Use Bootstrap's API to close the dropdown
                                            $('.dropdown-toggle').dropdown('hide');
                                        }, 300);
                                    }
                                
                                // Make the select visible
                                dropdownToggle.scrollIntoView({ behavior: 'smooth', block: 'center' });
                            } catch (e) {
                                console.error('Error selecting Bootstrap option:', e);
                            }
                            
                            break; // Stop after first match in this combobox
                        }
                    }
                    
                    if (matchFound) {
                        result.matchedComboboxes++;
                    }
                }
                
                // Process Select2 elements
                for (let i = 0; i < select2Elements.length; i++) {
                    const select2Container = select2Elements[i].closest('.select2-container');
                    if (!select2Container || !select2Container.offsetParent) continue;
                    
                    result.searchedComboboxes++;
                    
                    // Find the original select element
                    const select2Id = select2Container.getAttribute('data-select2-id');
                    const selectId = select2Container.getAttribute('id') ? select2Container.getAttribute('id').replace('select2-', '') : null;
                    
                    // Try different methods to find the original select
                    let selectElement = null;
                    if (select2Id) {
                        selectElement = document.querySelector('select[data-select2-id="' + select2Id + '"]');
                    }
                    if (!selectElement && selectId) {
                        selectElement = document.getElementById(selectId);
                    }
                    if (!selectElement) {
                        // Try to find by proximity
                        selectElement = select2Container.previousElementSibling;
                        if (selectElement && selectElement.tagName !== 'SELECT') {
                            selectElement = null;
                        }
                    }
                    
                    if (!selectElement) continue;
                    
                    // Get the combobox name
                    const comboboxName = selectElement.name || selectElement.id || ('select2_' + i);
                    
                    // Get options from the select element
                    const options = selectElement.options;
                    let matchFound = false;
                    let matchIndex = -1;
                    
                    for (let j = 0; j < options.length; j++) {
                        const option = options[j];
                        const optionText = option.text || option.innerHTML || '';
                        
                        // Normalize the option text for comparison
                        const normalizedOptionText = normalizeText(optionText);
                        
                        // Check if the option text contains the search text
                        if (normalizedOptionText.includes('${normalizedSearchText}')) {
                            matchFound = true;
                            matchIndex = j;
                            matchesFound++;
                            
                            // Store match details
                            result.details.push({
                                comboboxName: comboboxName,
                                matchedItem: optionText,
                                originalIndex: j
                            });
                            
                            // Try to select this option
                            try {
                                // Set the value directly on the original select
                                selectElement.selectedIndex = j;
                                
                                // Trigger change event
                                const event = new Event('change', { bubbles: true });
                                selectElement.dispatchEvent(event);
                                
                                // Update select2 if jQuery is available
                                if (typeof $ !== 'undefined') {
                                    $(selectElement).trigger('change.select2');
                                    
                                    // Force the select2 dropdown to close
                                    setTimeout(function() {
                                        // Make sure dropdown is closed
                                        try {
                                            // 1. Use select2's API to close
                                            $(selectElement).select2('close');
                                            
                                            // 2. Click outside
                                            document.body.click();
                                            
                                            // 3. Escape key
                                            document.dispatchEvent(new KeyboardEvent('keydown', {
                                                key: 'Escape',
                                                code: 'Escape',
                                                keyCode: 27,
                                                which: 27,
                                                bubbles: true
                                            }));
                                        } catch(e) {
                                            console.error('Error closing select2 dropdown:', e);
                                        }
                                    }, 300);
                                }
                                
                                // Make the select visible
                                select2Container.scrollIntoView({ behavior: 'smooth', block: 'center' });
                            } catch (e) {
                                console.error('Error selecting Select2 option:', e);
                            }
                            
                            break; // Stop after first match in this combobox
                        }
                    }
                    
                    if (matchFound) {
                        result.matchedComboboxes++;
                    }
                }
                
                // Process custom dropdowns and other dropdown implementations
                for (let i = 0; i < customDropdowns.length; i++) {
                    const dropdown = customDropdowns[i];
                    if (!dropdown.offsetParent) continue; // Skip if not visible
                    
                    result.searchedComboboxes++;
                    
                    // Get dropdown name
                    const comboboxName = dropdown.getAttribute('aria-label') || 
                                        dropdown.getAttribute('title') || 
                                        dropdown.textContent || 
                                        ('custom_dropdown_' + i);
                    
                    // Find dropdown items or options
                    const dropdownItems = [];
                    
                    // Try to find items by common dropdown patterns
                    const itemsContainer = dropdown.nextElementSibling || 
                                          dropdown.querySelector('.dropdown-menu');
                    
                    // Try to find by aria-labelledby if dropdown has id
                    let foundByAria = false;
                    if (dropdown.id) {
                        const ariaContainer = document.querySelector('[aria-labelledby="' + dropdown.id + '"]');
                        if (ariaContainer) {
                            foundByAria = true;
                            itemsContainer = ariaContainer;
                        }
                    }
                                          
                    if (itemsContainer) {
                        // Get all items in the dropdown
                        const items = itemsContainer.querySelectorAll(
                            '.dropdown-item, li, [role="option"], option, .dropdown-option'
                        );
                        
                        for (let j = 0; j < items.length; j++) {
                            dropdownItems.push({
                                element: items[j],
                                text: items[j].textContent || ''
                            });
                        }
                    }
                    
                    // If no items found, check if the dropdown itself has a value
                    if (dropdownItems.length === 0 && dropdown.textContent) {
                        // This might be showing the current selection
                        continue;
                    }
                    
                    // Search dropdown items
                    let matchFound = false;
                    
                    for (let j = 0; j < dropdownItems.length; j++) {
                        const item = dropdownItems[j];
                        const itemText = item.text || '';
                        
                        // Normalize the item text for comparison
                        const normalizedItemText = normalizeText(itemText);
                        
                        // Check if the item text contains the search text
                        if (normalizedItemText.includes('${normalizedSearchText}')) {
                            matchFound = true;
                            matchesFound++;
                            
                            // Store match details
                            result.details.push({
                                comboboxName: comboboxName,
                                matchedItem: itemText,
                                originalIndex: j
                            });
                            
                            // Try to select this option
                            try {
                                // Click the dropdown to open it
                                dropdown.click();
                                
                                // Short delay to let the dropdown open
                                setTimeout(function() {
                                    // Click the matching item
                                    item.element.click();
                                    
                                    // Make the dropdown visible
                                    dropdown.scrollIntoView({ behavior: 'smooth', block: 'center' });
                                    
                                    // Force close the dropdown after selection
                                    setTimeout(function() {
                                        // 1. Click outside
                                        document.body.click();
                                        
                                        // 2. Escape key
                                        document.dispatchEvent(new KeyboardEvent('keydown', {
                                            key: 'Escape',
                                            code: 'Escape',
                                            keyCode: 27,
                                            which: 27,
                                            bubbles: true
                                        }));
                                        
                                        // 3. If jQuery is available, try dropdown API
                                        if (typeof $ !== 'undefined') {
                                            $('.dropdown-toggle').dropdown('hide');
                                        }
                                    }, 300);
                                }, 100);
                            } catch (e) {
                                console.error('Error selecting custom dropdown item:', e);
                            }
                            
                            break; // Stop after first match
                        }
                    }
                    
                    if (matchFound) {
                        result.matchedComboboxes++;
                    }
                }
                
                // Update result status
                result.found = (matchesFound > 0);
                
                return result;
            } catch (e) {
                console.error('Error searching comboboxes:', e);
                result.error = e.toString();
                return result;
            }
        })();
        """.trimIndent()
        
        // Execute the search script
        webView.evaluateJavascript(searchScript) { result ->
            try {
                // Process the result
                val cleanResult = result.trim().removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")
                
                // Parse the JSON result
                val jsonResult = org.json.JSONObject(cleanResult)
                val found = jsonResult.optBoolean("found", false)
                val totalComboboxes = jsonResult.optInt("totalComboboxes", 0)
                val matchedComboboxes = jsonResult.optInt("matchedComboboxes", 0)
                
                if (found) {
                    // Get the details of the matches
                    val detailsArray = jsonResult.optJSONArray("details")
                    if (detailsArray != null && detailsArray.length() > 0) {
                        // Process the first match
                        val firstMatch = detailsArray.getJSONObject(0)
                        val comboboxName = firstMatch.optString("comboboxName", "")
                        val matchedItem = firstMatch.optString("matchedItem", "")
                        
                        // Callback for match found
                        onItemFound?.invoke(comboboxName, matchedItem)
                        
                        // If the select picker needs additional handling
                        enhanceComboboxDisplay()
                    }
                } else {
                    // No matches found
                    onNoResults?.invoke()
                }
                
                // Wait a bit and then finalize the search
                Handler(Looper.getMainLooper()).postDelayed({
                    onSearchComplete?.invoke()
                }, 500)
                
            } catch (e: Exception) {
                // Error processing results
                onNoResults?.invoke()
            }
        }
    }
    
    private fun enhanceComboboxDisplay() {
        val enhanceScript = """
        (function() {
            try {
                // Force close any open dropdowns first
                // 1. Send Escape key to close dropdowns
                document.dispatchEvent(new KeyboardEvent('keydown', {
                    key: 'Escape',
                    code: 'Escape',
                    keyCode: 27,
                    which: 27,
                    bubbles: true
                }));
                
                // 2. Click on body to close dropdowns
                document.body.click();
                
                // 3. Call blur() on any focused select
                const focused = document.querySelector('select:focus');
                if (focused) {
                    focused.blur();
                }
                
                // A bit more delay before showing enhancements
                setTimeout(function() {
                    // For Bootstrap selects
                    if (typeof $ !== 'undefined') {
                        // For Bootstrap select
                        if ($('.selectpicker').length > 0) {
                            $('.selectpicker').selectpicker('refresh');
                        }
                        
                        // For select2 plugin - force close first
                        if ($('.select2').length > 0) {
                            $('.select2').select2('close');
                        }
                    }
                    
                    // Find all visible selects
                    const visibleSelects = document.querySelectorAll('select:not([style*="display: none"])');
                    for (let i = 0; i < visibleSelects.length; i++) {
                        try {
                            // Make sure the selection is visible but the dropdown is closed
                            visibleSelects[i].scrollIntoView({behavior: 'smooth', block: 'center'});
                        } catch(e) {
                            console.error('Error enhancing select:', e);
                        }
                    }
                }, 300);
                
                return 'ENHANCE_COMPLETED';
            } catch (e) {
                return 'ENHANCE_ERROR: ' + e.message;
            }
        })();
        """.trimIndent()
        
        // Execute the enhancement script
        webView.evaluateJavascript(enhanceScript) { _ -> }
    }
    
    /**
     * Normalize text for case-insensitive and accent-insensitive comparison
     * 
     * @param text Text to normalize
     * @return Normalized text
     */
    private fun normalizeText(text: String): String {
        if (text.isBlank()) return ""
        
        // Convert to lowercase first
        var normalizedText = text.lowercase()
        
        // Create a map of Turkish characters to their Latin equivalents
        val replacements = mapOf(
            "ğ" to "g", "Ğ" to "g",
            "ü" to "u", "Ü" to "u",
            "ş" to "s", "Ş" to "s",
            "ı" to "i", "I" to "i",
            "i" to "i", "İ" to "i",
            "ö" to "o", "Ö" to "o",
            "ç" to "c", "Ç" to "c"
        )
        
        // Apply all replacements
        for ((original, replacement) in replacements) {
            normalizedText = normalizedText.replace(original, replacement)
        }
        
        // Additional cleanup - normalize Unicode characters to ASCII equivalents where possible
        return java.text.Normalizer.normalize(normalizedText, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }
}
