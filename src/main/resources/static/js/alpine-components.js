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
    if (globalThis.Alpine && event.detail.target) {
        Alpine.destroyTree(event.detail.target)
        Alpine.initTree(event.detail.target)
    }
});

// Register Alpine components
// Must register before Alpine processes the DOM
function registerAlpineComponents() {
    if (globalThis._alpineComponentsRegistered) return
    globalThis._alpineComponentsRegistered = true

    registerBasicStates()
    registerNavigationStates()
    registerFormComponents()
}

function registerBasicStates() {
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

    // CSP-safe combobox for picking a Chart of Accounts entry. Each <div x-data="accountPicker">
    // carries data-initial-id and data-initial-label set by Thymeleaf. Server search via
    // GET /accounts/search?q=... returns at most 10 results.
    Alpine.data('accountPicker', () => ({
        open: false,
        results: [],
        label: '',
        selectedId: '',
        init() {
            this.label = this.$el.dataset.initialLabel || ''
            this.selectedId = this.$el.dataset.initialId || ''
        },
        focusPicker() {
            this.open = true
            if (this.results.length === 0) this.search()
        },
        search() {
            const q = encodeURIComponent(this.label || '')
            const self = this
            fetch('/accounts/search?q=' + q, { headers: { 'Accept': 'application/json' } })
                .then(r => r.ok ? r.json() : [])
                .then(data => { self.results = data })
        },
        select(a) {
            this.selectedId = a.id
            this.label = a.code + ' - ' + a.name
            this.results = []
            this.open = false
        }
    }))

    // CSP-safe combobox for picking a Client. Same shape as accountPicker, hits
    // GET /clients/search?q=... which returns at most 10 results. Used by invoice
    // forms where the client list is unbounded in production.
    Alpine.data('clientPicker', () => ({
        open: false,
        results: [],
        label: '',
        selectedId: '',
        init() {
            this.label = this.$el.dataset.initialLabel || ''
            this.selectedId = this.$el.dataset.initialId || ''
        },
        focusPicker() {
            this.open = true
            if (this.results.length === 0) this.search()
        },
        search() {
            const q = encodeURIComponent(this.label || '')
            const self = this
            fetch('/clients/search?q=' + q, { headers: { 'Accept': 'application/json' } })
                .then(r => r.ok ? r.json() : [])
                .then(data => { self.results = data })
        },
        select(c) {
            this.selectedId = c.id
            this.label = c.code + ' - ' + c.name
            this.results = []
            this.open = false
        }
    }))

    // CSP-safe combobox for picking a Vendor. Same shape as clientPicker, hits
    // GET /vendors/search?q=... which returns at most 10 results. Used by bill
    // forms where the vendor list is unbounded in production.
    Alpine.data('vendorPicker', () => ({
        open: false,
        results: [],
        label: '',
        selectedId: '',
        init() {
            this.label = this.$el.dataset.initialLabel || ''
            this.selectedId = this.$el.dataset.initialId || ''
        },
        focusPicker() {
            this.open = true
            if (this.results.length === 0) this.search()
        },
        search() {
            const q = encodeURIComponent(this.label || '')
            const self = this
            fetch('/vendors/search?q=' + q, { headers: { 'Accept': 'application/json' } })
                .then(r => r.ok ? r.json() : [])
                .then(data => { self.results = data })
        },
        select(v) {
            this.selectedId = v.id
            this.label = v.code + ' - ' + v.name
            this.results = []
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
}

function registerNavigationStates() {
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
}

function registerFormComponents() {
    // Indonesian number formatter for thousand separators (uses . as separator)
    const idNumberFormat = new Intl.NumberFormat('id-ID')

    // Transaction form state
    Alpine.data('transactionForm', () => ({
        init() {
            this.amount = Number.parseInt(this.$el.dataset.amount, 10) || 0
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
            this.amount = Number.parseInt(e.target.value.replaceAll(/\D/g, '')) || 0
            // Re-format the display
            e.target.value = this.amount > 0 ? new Intl.NumberFormat('id-ID').format(this.amount) : ''
            // Sync hidden input immediately (before HTMX reads it)
            const hiddenInput = document.getElementById('amountHidden')
            if (hiddenInput) {
                hiddenInput.value = this.amount
            }
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
            return idNumberFormat.format(this.amount)
        },

        // Getter - button text based on submitting state
        get submitButtonText() {
            return this.submitting ? 'Menyimpan...' : 'Simpan Draft'
        },

        // Method - called as event handler @input="updateAmount"
        updateAmount(e) {
            this.amount = Number.parseInt(e.target.value.replaceAll(/\D/g, '')) || 0
            e.target.value = this.amount ? idNumberFormat.format(this.amount) : ''
        },

        // Method - for variable inputs in DETAILED templates
        updateVariable(e) {
            const input = e.target
            const rawValue = input.value.replaceAll(/\D/g, '')
            const hiddenInput = input.nextElementSibling
            if (hiddenInput?.classList.contains('var-value')) {
                hiddenInput.value = rawValue
            }
            input.value = rawValue ? idNumberFormat.format(Number.parseInt(rawValue, 10)) : ''
        },

        // Method - close the modal dialog
        closeModal() {
            const dialog = document.getElementById('quick-transaction-modal')
            if (dialog) dialog.close()
        },

        // Helper - collect account mappings from form data
        collectAccountMappings(formData) {
            const mappings = {}
            for (const [key, value] of formData.entries()) {
                const match = key.match(/accountMapping\[([^\]]+)\]/)
                if (match && value) {
                    mappings[match[1]] = value
                }
            }
            return mappings
        },

        // Helper - collect variable values for DETAILED templates
        collectVariables(formData) {
            const variables = {}
            for (const [key, value] of formData.entries()) {
                if (key.startsWith('var_') && value) {
                    const cleanValue = value.replaceAll(/\D/g, '')
                    if (cleanValue) {
                        variables[key.substring(4)] = Number.parseInt(cleanValue, 10)
                    }
                }
            }
            return variables
        },

        // Helper - build headers with CSRF token
        buildHeaders() {
            const csrfToken = document.querySelector('meta[name="_csrf"]')?.content
            const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content
            const headers = { 'Content-Type': 'application/json' }
            if (csrfToken && csrfHeader) {
                headers[csrfHeader] = csrfToken
            }
            return headers
        },

        // Method - submit the quick transaction form
        async submitForm(e) {
            if (e?.preventDefault) e.preventDefault()
            if (this.submitting) return

            this.submitting = true
            const form = document.getElementById('quick-transaction-form')

            try {
                const formData = new FormData(form)
                const variables = this.collectVariables(formData)
                const data = {
                    templateId: formData.get('templateId'),
                    amount: Number.parseInt(formData.get('amount'), 10) || 0,
                    description: formData.get('description'),
                    transactionDate: formData.get('transactionDate'),
                    referenceNumber: formData.get('referenceNumber') || '',
                    notes: formData.get('notes') || '',
                    accountMappings: this.collectAccountMappings(formData),
                    ...(Object.keys(variables).length > 0 && { variables })
                }

                const response = await fetch('/transactions/api', {
                    method: 'POST',
                    headers: this.buildHeaders(),
                    body: JSON.stringify(data)
                })

                if (response.ok) {
                    const result = await response.json()
                    document.getElementById('quick-transaction-modal')?.close()
                    window.location.href = '/transactions/' + result.transactionId
                } else {
                    const errorText = await response.text()
                    alert('Gagal menyimpan: ' + errorText)
                }
            } catch (err) {
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

    // Free-form journal entry form. State-driven via Alpine reactive lines
    // (x-for in template). Each line has its own accountPicker-style combobox
    // backed by GET /accounts/search; no full COA dump in the page.
    function blankJournalLine() {
        return {
            label: '',
            selectedId: '',
            results: [],
            pickerOpen: false,
            debit: 0,
            credit: 0,
            debitText: '0',
            creditText: '0'
        }
    }
    Alpine.data('journalEntryForm', () => ({
        transactionDate: new Date().toISOString().split('T')[0],
        description: '',
        category: '',
        submitting: false,
        errorMessage: '',
        lines: [blankJournalLine(), blankJournalLine()],

        get totalDebit() {
            return this.lines.reduce((s, l) => s + (Number.parseInt(l.debit, 10) || 0), 0)
        },
        get totalCredit() {
            return this.lines.reduce((s, l) => s + (Number.parseInt(l.credit, 10) || 0), 0)
        },
        get isBalanced() {
            return this.totalDebit === this.totalCredit && this.totalDebit > 0
        },
        get hasAmounts() {
            return this.totalDebit > 0 || this.totalCredit > 0
        },
        get showDifference() {
            return this.hasAmounts && !this.isBalanced
        },
        get balanceClass() {
            return this.isBalanced ? 'text-gray-900' : 'text-red-600'
        },
        get formattedTotalDebit() {
            return new Intl.NumberFormat('id-ID').format(this.totalDebit)
        },
        get formattedTotalCredit() {
            return new Intl.NumberFormat('id-ID').format(this.totalCredit)
        },
        get formattedDifference() {
            return new Intl.NumberFormat('id-ID').format(Math.abs(this.totalDebit - this.totalCredit))
        },
        get saveButtonText() {
            return this.submitting ? 'Menyimpan...' : 'Simpan Draft'
        },
        get postButtonText() {
            return this.submitting ? 'Menyimpan...' : 'Simpan & Posting'
        },

        // Combobox callbacks (per-line picker state)
        searchAccounts(line) {
            const q = encodeURIComponent(line.label || '')
            fetch('/accounts/search?q=' + q, { headers: { 'Accept': 'application/json' } })
                .then(r => r.ok ? r.json() : [])
                .then(data => { line.results = data })
        },
        focusLine(line) {
            line.pickerOpen = true
            if (!line.results || line.results.length === 0) this.searchAccounts(line)
        },
        selectAccount(line, a) {
            line.selectedId = a.id
            line.label = a.code + ' - ' + a.name
            line.results = []
            line.pickerOpen = false
        },

        // Amount handlers. We bind :value (one-way) and read $event.target.value
        // on input, since x-model on inputs inside x-for + late-init data tends
        // to lose sync with the typed text. The numeric side updates live so
        // totals stay accurate; reformat on blur to avoid fighting the cursor.
        // A row is debit-XOR-credit, so entering one zeroes the other.
        handleDebitInput(line, event) {
            line.debitText = event.target.value
            const raw = Number.parseInt(line.debitText.replaceAll(/\D/g, ''), 10) || 0
            line.debit = raw
            if (raw > 0) {
                line.credit = 0
                line.creditText = '0'
            }
        },
        handleCreditInput(line, event) {
            line.creditText = event.target.value
            const raw = Number.parseInt(line.creditText.replaceAll(/\D/g, ''), 10) || 0
            line.credit = raw
            if (raw > 0) {
                line.debit = 0
                line.debitText = '0'
            }
        },
        onAmountFocus(e) {
            if (e.target.value === '0') e.target.value = ''
        },
        onDebitBlur(line) {
            line.debitText = line.debit > 0
                ? new Intl.NumberFormat('id-ID').format(line.debit)
                : '0'
        },
        onCreditBlur(line) {
            line.creditText = line.credit > 0
                ? new Intl.NumberFormat('id-ID').format(line.credit)
                : '0'
        },

        addLine() {
            this.lines.push(blankJournalLine())
        },
        removeLine(idx) {
            if (this.lines.length > 2) this.lines.splice(idx, 1)
        },

        collectLines() {
            return this.lines.map(l => ({
                accountId: l.selectedId,
                debit: Number.parseInt(l.debit, 10) || 0,
                credit: Number.parseInt(l.credit, 10) || 0
            }))
        },

        validate() {
            this.errorMessage = ''
            if (!this.transactionDate) {
                this.errorMessage = 'Tanggal transaksi wajib diisi'
                return false
            }
            if (!this.description.trim()) {
                this.errorMessage = 'Deskripsi wajib diisi'
                return false
            }
            const lines = this.collectLines()
            for (let i = 0; i < lines.length; i++) {
                if (!lines[i].accountId) {
                    this.errorMessage = 'Baris ' + (i + 1) + ': pilih akun'
                    return false
                }
                if (lines[i].debit === 0 && lines[i].credit === 0) {
                    this.errorMessage = 'Baris ' + (i + 1) + ': isi debit atau kredit'
                    return false
                }
            }
            if (!this.isBalanced) {
                this.errorMessage = 'Jurnal tidak seimbang. Total debit harus sama dengan total kredit.'
                return false
            }
            return true
        },

        buildHeaders() {
            const csrfToken = document.querySelector('meta[name="_csrf"]')?.content
            const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content
            const headers = { 'Content-Type': 'application/json' }
            if (csrfToken && csrfHeader) {
                headers[csrfHeader] = csrfToken
            }
            return headers
        },

        buildPayload() {
            return {
                transactionDate: this.transactionDate,
                description: this.description.trim(),
                category: this.category || null,
                lines: this.collectLines()
            }
        },

        async saveDraft() {
            if (!this.validate()) return
            this.submitting = true
            try {
                const response = await fetch('/transactions/journal-entry', {
                    method: 'POST',
                    headers: this.buildHeaders(),
                    body: JSON.stringify(this.buildPayload())
                })
                if (!response.ok) {
                    const err = await response.json()
                    throw new Error(err.message || err.error || 'Gagal menyimpan jurnal')
                }
                const result = await response.json()
                window.location.href = '/transactions/' + result.transactionId
            } catch (e) {
                this.errorMessage = e.message
            } finally {
                this.submitting = false
            }
        },

        async saveAndPost() {
            if (!this.validate()) return
            this.submitting = true
            try {
                const createResponse = await fetch('/transactions/journal-entry', {
                    method: 'POST',
                    headers: this.buildHeaders(),
                    body: JSON.stringify(this.buildPayload())
                })
                if (!createResponse.ok) {
                    const err = await createResponse.json()
                    throw new Error(err.message || err.error || 'Gagal menyimpan jurnal')
                }
                const draft = await createResponse.json()

                const postHeaders = this.buildHeaders()
                delete postHeaders['Content-Type']
                const postResponse = await fetch('/transactions/api/' + draft.transactionId + '/post', {
                    method: 'POST',
                    headers: postHeaders
                })
                if (!postResponse.ok) {
                    const err = await postResponse.json()
                    throw new Error(err.message || err.error || 'Gagal posting jurnal')
                }
                window.location.href = '/transactions/' + draft.transactionId
            } catch (e) {
                this.errorMessage = e.message
            } finally {
                this.submitting = false
            }
        }
    }))

    // Tax detail form - conditional sections based on tax type
    Alpine.data('taxDetailForm', () => ({
        taxType: '',
        idType: 'TIN',
        init() {
            this.taxType = this.$el.dataset.taxType || ''
            this.idType = this.$el.dataset.idType || 'TIN'
            // Sync the select elements with Alpine state
            const taxTypeSelect = this.$el.querySelector('[data-testid="tax-type-select"]')
            if (taxTypeSelect && this.taxType) {
                taxTypeSelect.value = this.taxType
            }
            const idTypeSelect = this.$el.querySelector('[data-testid="counterparty-id-type"]')
            if (idTypeSelect && this.idType) {
                idTypeSelect.value = this.idType
            }
        },
        get isPpn() {
            return this.taxType === 'PPN_KELUARAN' || this.taxType === 'PPN_MASUKAN'
        },
        get isPph() {
            return this.taxType === 'PPH_21' || this.taxType === 'PPH_23' || this.taxType === 'PPH_42'
        },
        get hasType() {
            return this.taxType !== ''
        },
        get isTin() {
            return this.idType === 'TIN'
        },
        get isNik() {
            return this.idType === 'NIK'
        }
    }))

    // Account form - auto-suggest permanent based on account type
    Alpine.data('accountForm', () => ({
        isNewAccount: false,
        init() {
            // Read from data attribute set by Thymeleaf
            this.isNewAccount = this.$el.dataset.newAccount === 'true'
        },
        suggestPermanent(accountType) {
            if (!this.isNewAccount) return
            const permanentTypes = ['ASSET', 'LIABILITY', 'EQUITY']
            const permanentCheckbox = document.getElementById('permanent')
            if (permanentCheckbox) {
                permanentCheckbox.checked = permanentTypes.includes(accountType)
            }
        }
    }))
}

// Hybrid approach: register immediately if Alpine exists,
// and also listen for alpine:init for deferred script loading
if (globalThis.Alpine) {
    registerAlpineComponents()
} else {
    document.addEventListener('alpine:init', registerAlpineComponents)
}
