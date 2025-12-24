/**
 * Alpine.js CSP-compatible component definitions.
 *
 * All x-data objects must be registered here to avoid using Function() constructor
 * which requires 'unsafe-eval' in Content Security Policy.
 *
 * Usage in templates:
 *   OLD: <div x-data="{ open: false }">
 *   NEW: <div x-data="toggleState">
 */

// Reinitialize Alpine.js components after HTMX settles
// See: https://github.com/alpinejs/alpine/discussions/4478
document.addEventListener('htmx:afterSettle', (event) => {
    // Destroy and reinitialize Alpine components in the swapped content
    if (window.Alpine && event.detail.target) {
        Alpine.destroyTree(event.detail.target)
        Alpine.initTree(event.detail.target)
    }
});

// Register Alpine components
// Must register before Alpine processes the DOM
function registerAlpineComponents() {
    if (window._alpineComponentsRegistered) return
    window._alpineComponentsRegistered = true
    // Simple toggle state (open/closed)
    Alpine.data('toggleState', () => ({
        open: false,
        toggle() {
            this.open = !this.open
        },
        close() {
            this.open = false
        }
    }))

    // Toggle state with hasQuery flag (for search filters)
    Alpine.data('searchFilterState', () => ({
        open: false,
        hasQuery: false,
        toggle() {
            this.open = !this.open
        },
        openDropdown() {
            this.open = true
        },
        closeDropdown() {
            this.open = false
        },
        updateHasQuery(event) {
            this.hasQuery = event.target.value.length > 0
        }
    }))

    // Sidebar state
    Alpine.data('sidebarState', () => ({
        sidebarOpen: false,
        toggleSidebar() {
            this.sidebarOpen = !this.sidebarOpen
        },
        openSidebar() {
            this.sidebarOpen = true
        },
        closeSidebar() {
            this.sidebarOpen = false
        }
    }))

    // Expandable section
    Alpine.data('expandableState', () => ({
        expanded: false,
        toggleExpanded() {
            this.expanded = !this.expanded
        }
    }))

    // Show/hide state
    Alpine.data('showState', () => ({
        show: false,
        toggleShow() {
            this.show = !this.show
        },
        closeShow() {
            this.show = false
        }
    }))

    // ID type selector
    Alpine.data('idTypeSelector', () => ({
        idType: '',
        initFromElement(el) {
            this.idType = el.value || ''
        },
        updateFromEvent(event) {
            this.idType = event.target.value
        }
    }))

    // Void transaction form
    Alpine.data('voidForm', () => ({
        voidReason: '',
        confirmVoid: false
    }))

    // Percentage toggle for salary components
    Alpine.data('percentageToggle', () => ({
        isPercentage: false,
        init() {
            // Initialize from data attribute
            const initial = this.$el.dataset.initialPercentage
            this.isPercentage = initial === 'true'
        },
        setFixed() {
            this.isPercentage = false
        },
        setPercentage() {
            this.isPercentage = true
        }
    }))

    // Persistent navigation state for accounting section
    Alpine.data('navAkuntansi', () => ({
        open: Alpine.$persist(true).as('nav-akuntansi'),
        toggle() {
            this.open = !this.open
        }
    }))

    // Persistent navigation state for reports section
    Alpine.data('navLaporan', () => ({
        open: Alpine.$persist(false).as('nav-laporan'),
        toggle() {
            this.open = !this.open
        }
    }))

    // Persistent navigation state for projects section
    Alpine.data('navProyek', () => ({
        open: Alpine.$persist(false).as('nav-proyek'),
        toggle() {
            this.open = !this.open
        }
    }))

    // Persistent navigation state for inventory section
    Alpine.data('navInventori', () => ({
        open: Alpine.$persist(false).as('nav-inventori'),
        toggle() {
            this.open = !this.open
        }
    }))

    // Persistent navigation state for payroll section
    Alpine.data('navPayroll', () => ({
        open: Alpine.$persist(false).as('nav-payroll'),
        toggle() {
            this.open = !this.open
        }
    }))

    // Persistent navigation state for master data section
    Alpine.data('navMaster', () => ({
        open: Alpine.$persist(false).as('nav-master'),
        toggle() {
            this.open = !this.open
        }
    }))

    // Open by default navigation section
    Alpine.data('navOpenDefault', () => ({
        open: true,
        toggle() {
            this.open = !this.open
        }
    }))

    // Closed by default navigation section
    Alpine.data('navClosedDefault', () => ({
        open: false,
        toggle() {
            this.open = !this.open
        }
    }))

    // Transaction form state
    Alpine.data('transactionForm', () => ({
        init() {
            this.amount = parseInt(this.$el.dataset.amount) || 0
            this.description = this.$el.dataset.description || ''
            // Initialize the display input with formatted value
            const displayInput = this.$el.querySelector('#amount')
            if (displayInput && this.amount > 0) {
                displayInput.value = new Intl.NumberFormat('id-ID').format(this.amount)
            }
            // Initialize description input
            const descInput = this.$el.querySelector('#description')
            if (descInput && this.description) {
                descInput.value = this.description
            }
        },
        amount: 0,
        description: '',
        submitting: false,

        // CSP-compatible getters (operators not supported in CSP build)
        get notSubmitting() {
            return !this.submitting
        },

        getSubmitButtonText() {
            if (this.submitting) {
                return 'Menyimpan...'
            }
            return 'Simpan Draft'
        },

        getSubmitPostButtonText() {
            if (this.submitting) {
                return 'Memproses...'
            }
            return 'Simpan & Posting'
        },

        // Getter - accessed as property in :value="formattedAmount"
        get formattedAmount() {
            return this.amount > 0 ? new Intl.NumberFormat('id-ID').format(this.amount) : ''
        },

        // Method - called as event handler @input="updateAmount"
        updateAmount(e) {
            // Parse the raw numeric value
            this.amount = parseInt(e.target.value.replace(/\D/g, '')) || 0
            // Re-format the display
            e.target.value = this.amount > 0 ? new Intl.NumberFormat('id-ID').format(this.amount) : ''
            // Trigger HTMX preview update
            this.$dispatch('amount-changed')
        },

        // Method - for description input
        updateDescription(e) {
            this.description = e.target.value
        },

        // Method - dispatch var changed event for HTMX preview
        dispatchVarChanged() {
            this.$dispatch('var-changed')
        },

        // Method - dispatch account changed event for HTMX preview
        dispatchAccountChanged() {
            this.$dispatch('account-changed')
        }
    }))

    // Quick transaction form state
    Alpine.data('quickTransactionForm', () => ({
        amount: 0,
        submitting: false,

        // Getter - accessed as property
        get formattedAmount() {
            if (!this.amount) return ''
            return this.amount.toString().replace(/\B(?=(\d{3})+(?!\d))/g, '.')
        },

        // Getter - button text based on submitting state
        get submitButtonText() {
            return this.submitting ? 'Menyimpan...' : 'Simpan Draft'
        },

        // Method - called as event handler @input="updateAmount"
        updateAmount(e) {
            this.amount = parseInt(e.target.value.replace(/[^\d]/g, '')) || 0
            e.target.value = this.amount ? this.amount.toString().replace(/\B(?=(\d{3})+(?!\d))/g, '.') : ''
        },

        // Method - for variable inputs in DETAILED templates
        updateVariable(e) {
            const input = e.target
            const rawValue = input.value.replace(/[^\d]/g, '')
            const hiddenInput = input.nextElementSibling
            if (hiddenInput && hiddenInput.classList.contains('var-value')) {
                hiddenInput.value = rawValue
            }
            input.value = rawValue ? rawValue.replace(/\B(?=(\d{3})+(?!\d))/g, '.') : ''
        },

        // Method - close the modal dialog
        closeModal() {
            const dialog = document.getElementById('quick-transaction-modal')
            if (dialog) dialog.close()
        },

        // Method - submit the quick transaction form
        async submitForm(e) {
            console.log('submitForm called', e)
            if (e && e.preventDefault) e.preventDefault()
            if (this.submitting) return

            this.submitting = true
            const form = document.getElementById('quick-transaction-form')
            console.log('form found:', form)

            try {
                const formData = new FormData(form)
                const data = {
                    templateId: formData.get('templateId'),
                    amount: parseInt(formData.get('amount')) || 0,
                    description: formData.get('description'),
                    transactionDate: formData.get('transactionDate'),
                    referenceNumber: formData.get('referenceNumber') || '',
                    notes: formData.get('notes') || '',
                    accountMappings: {}
                }

                // Collect account mappings
                for (const [key, value] of formData.entries()) {
                    const match = key.match(/accountMapping\[([^\]]+)\]/)
                    if (match && value) {
                        data.accountMappings[match[1]] = value
                    }
                }

                // Collect variable values for DETAILED templates
                const variables = {}
                for (const [key, value] of formData.entries()) {
                    if (key.startsWith('var_') && value) {
                        const varName = key.substring(4)
                        const cleanValue = value.replace(/[^0-9]/g, '')
                        if (cleanValue) {
                            variables[varName] = parseInt(cleanValue)
                        }
                    }
                }
                if (Object.keys(variables).length > 0) {
                    data.variables = variables
                }

                // Get CSRF token from meta tags
                const csrfToken = document.querySelector('meta[name="_csrf"]')?.content
                const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content

                const headers = { 'Content-Type': 'application/json' }
                if (csrfToken && csrfHeader) {
                    headers[csrfHeader] = csrfToken
                }

                const response = await fetch('/transactions/api', {
                    method: 'POST',
                    headers: headers,
                    body: JSON.stringify(data)
                })

                console.log('Response status:', response.status)
                if (response.ok) {
                    const result = await response.json()
                    console.log('Transaction created:', result)
                    const dialog = document.getElementById('quick-transaction-modal')
                    if (dialog) dialog.close()
                    window.location.href = '/transactions/' + result.id
                } else {
                    const errorText = await response.text()
                    console.error('Quick transaction error:', response.status, errorText)
                    alert('Gagal menyimpan: ' + errorText)
                }
            } catch (err) {
                console.error('Quick transaction exception:', err)
                alert('Gagal menyimpan: ' + err.message)
            } finally {
                this.submitting = false
            }
        },

        // Method - dispatch account changed event
        dispatchAccountChanged() {
            this.$dispatch('account-changed')
        }
    }))
}

// Hybrid approach: register immediately if Alpine exists,
// and also listen for alpine:init for deferred script loading
if (window.Alpine) {
    registerAlpineComponents()
} else {
    document.addEventListener('alpine:init', registerAlpineComponents)
}
