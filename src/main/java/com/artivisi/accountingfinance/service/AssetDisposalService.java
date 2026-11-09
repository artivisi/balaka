package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.entity.AssetDisposal;
import com.artivisi.accountingfinance.entity.AssetDisposalStatus;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.DepreciationEntry;
import com.artivisi.accountingfinance.entity.DisposalType;
import com.artivisi.accountingfinance.entity.FixedAsset;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.entity.TransactionSequence;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.service.event.TransactionPostedEvent;
import com.artivisi.accountingfinance.service.event.TransactionVoidedEvent;
import com.artivisi.accountingfinance.repository.AssetDisposalRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.DepreciationEntryRepository;
import com.artivisi.accountingfinance.repository.FixedAssetRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.repository.TransactionSequenceRepository;
import com.artivisi.accountingfinance.security.LogSanitizer;
import com.artivisi.accountingfinance.service.DisposalTaxCalculator.DisposalTaxEffect;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Disposal flow. create() writes the record and a DRAFT journal; the asset stays on the register but locked:
 * no edit, no depreciation, no second disposal. It becomes DISPOSED when the journal is posted, by the transaction
 * screens or the API, and returns to the register when that journal is voided. Read access for later reports is
 * {@link AssetRegisterQueryService}.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AssetDisposalService {

    public static final String SALE_TEMPLATE = "Pelepasan Aset Tetap";
    public static final String NON_SALE_TEMPLATE = "Penghapusan Aset Tetap";
    public static final String SOURCE_DOCUMENT_TYPE = "ASSET_DISPOSAL";
    public static final String GAIN_ACCOUNT = "Laba Penjualan Aset";
    public static final String LOSS_ACCOUNT = "Rugi Penjualan Aset";
    public static final String NON_DEDUCTIBLE_ACCOUNT = "Beban Tidak Dapat Dikurangkan";

    private static final String SEQUENCE_TYPE = "ASSET_DISPOSAL";
    private static final String SEQUENCE_PREFIX = "DSP";

    private final FixedAssetRepository fixedAssetRepository;
    private final AssetDisposalRepository disposalRepository;
    private final DepreciationEntryRepository depreciationEntryRepository;
    private final ChartOfAccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionSequenceRepository sequenceRepository;
    private final TransactionService transactionService;
    private final DocumentPostingService documentPostingService;
    private final DisposalTaxCalculator taxCalculator;

    public record CreateDisposal(UUID assetId, DisposalType type, LocalDate date, BigDecimal proceeds,
                                 UUID proceedsAccountId, String counterparty, String reason, String createdBy) {
    }

    @Transactional(readOnly = true)
    public DisposalTaxEffect preview(UUID assetId, DisposalType type, BigDecimal proceeds) {
        FixedAsset asset = findAsset(assetId);
        return taxCalculator.calculate(type, proceeds, asset.getBookValue());
    }

    @Transactional(readOnly = true)
    public DisposalTaxEffect taxEffect(AssetDisposal disposal) {
        return taxCalculator.calculate(disposal.getDisposalType(),
                disposal.getDisposalType() == DisposalType.SOLD ? disposal.getProceeds() : null,
                disposal.getBookValue());
    }

    public AssetDisposal create(CreateDisposal command) {
        FixedAsset asset = findAsset(command.assetId());
        assertDisposable(asset, command.date());

        String reason = AssetMovementRecorder.normalize(command.reason());
        if (reason == null) {
            throw new IllegalArgumentException("Alasan pelepasan wajib diisi");
        }
        String counterparty = AssetMovementRecorder.normalize(command.counterparty());
        if (counterparty == null && command.type() != DisposalType.WRITTEN_OFF) {
            throw new IllegalArgumentException("Nama pembeli atau penerima wajib diisi");
        }
        DisposalTaxEffect effect = taxCalculator.calculate(command.type(), command.proceeds(), asset.getBookValue());
        ChartOfAccount proceedsAccount = resolveProceedsAccount(command);

        AssetDisposal disposal = new AssetDisposal();
        disposal.setRecordNumber(nextRecordNumber(command.date().getYear()));
        disposal.setFixedAsset(asset);
        disposal.setDisposalType(command.type());
        disposal.setDisposalDate(command.date());
        disposal.setReason(reason);
        disposal.setCounterparty(counterparty);
        disposal.setProceeds(effect.proceeds());
        disposal.setPurchaseCost(asset.getPurchaseCost());
        disposal.setAccumulatedDepreciation(asset.getAccumulatedDepreciation());
        disposal.setBookValue(asset.getBookValue());
        disposal.setGainLoss(effect.gainLoss());
        disposal.setProceedsAccount(proceedsAccount);
        disposal.setStatus(AssetDisposalStatus.DRAFT);
        disposal.setCreatedBy(command.createdBy());
        AssetDisposal saved = disposalRepository.save(disposal);

        saved.setTransaction(composeDraft(saved, asset));
        log.info("Asset disposal {} drafted for asset {}", saved.getRecordNumber(), LogSanitizer.sanitize(asset.getAssetCode()));
        return saved;
    }

    /** Removes the draft journal; the record stays as CANCELLED and the register unlocks. */
    public AssetDisposal cancel(UUID disposalId) {
        AssetDisposal disposal = findById(disposalId);
        if (!disposal.isDraft()) {
            throw new IllegalStateException("Hanya pelepasan berstatus draf yang dapat dibatalkan. "
                    + "Pelepasan yang sudah diposting dibatalkan dengan void jurnalnya.");
        }
        UUID transactionId = disposal.getTransaction().getId();
        disposal.setTransaction(null);
        disposal.setStatus(AssetDisposalStatus.CANCELLED);
        disposalRepository.flush();
        transactionService.delete(transactionId);
        return disposal;
    }

    @EventListener
    public void onTransactionPosted(TransactionPostedEvent event) {
        disposalRepository.findByTransactionId(event.transaction().getId()).ifPresent(disposal -> {
            FixedAsset asset = disposal.getFixedAsset();
            assertNoPendingDepreciation(asset, disposal);
            if (asset.getBookValue().compareTo(disposal.getBookValue()) != 0) {
                throw new IllegalStateException("Nilai buku aset " + asset.getAssetCode() + " berbeda dari catatan pelepasan "
                        + disposal.getRecordNumber() + ". Batalkan pelepasan lalu buat ulang.");
            }
            asset.applyDisposal(disposal);
            disposal.setStatus(AssetDisposalStatus.POSTED);
            log.info("Asset {} disposed by {}", LogSanitizer.sanitize(asset.getAssetCode()), disposal.getRecordNumber());
        });
    }

    /**
     * A PENDING entry can never post once the asset is DISPOSED, so the disposal is refused while one exists,
     * whatever its date. create() already refuses PENDING entries and generation skips an asset with a DRAFT
     * disposal; this catches entries that reached PENDING another way (data from before that rule).
     */
    private void assertNoPendingDepreciation(FixedAsset asset, AssetDisposal disposal) {
        List<DepreciationEntry> pending = depreciationEntryRepository.findByAssetId(asset.getId()).stream()
                .filter(DepreciationEntry::isPending)
                .toList();
        if (pending.isEmpty()) {
            return;
        }
        String periods = pending.stream().map(e -> e.getPeriodEnd().toString()).sorted().collect(Collectors.joining(", "));
        throw new IllegalStateException("Aset " + asset.getAssetCode() + " masih punya entri penyusutan PENDING (periode "
                + periods + "). Skip entri tersebut sebelum memposting pelepasan " + disposal.getRecordNumber()
                + ", atau batalkan pelepasan, posting penyusutannya, lalu buat ulang pelepasan.");
    }

    @EventListener
    public void onTransactionVoided(TransactionVoidedEvent event) {
        disposalRepository.findByTransactionId(event.transaction().getId()).ifPresent(disposal -> {
            disposal.getFixedAsset().reverseDisposal();
            disposal.setStatus(AssetDisposalStatus.VOIDED);
            log.info("Asset disposal {} voided", disposal.getRecordNumber());
        });
    }

    @Transactional(readOnly = true)
    public AssetDisposal findById(UUID id) {
        return disposalRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new EntityNotFoundException("Catatan pelepasan tidak ditemukan"));
    }

    private FixedAsset findAsset(UUID assetId) {
        return fixedAssetRepository.findByIdWithDetails(assetId)
                .orElseThrow(() -> new EntityNotFoundException("Aset tidak ditemukan"));
    }

    private void assertDisposable(FixedAsset asset, LocalDate date) {
        if (asset.isDisposed()) {
            throw new IllegalStateException("Aset sudah dilepas sebelumnya");
        }
        if (disposalRepository.existsByFixedAssetIdAndStatus(asset.getId(), AssetDisposalStatus.DRAFT)) {
            throw new IllegalStateException("Aset masih punya draf pelepasan. Posting atau batalkan draf tersebut.");
        }
        if (date == null) {
            throw new IllegalArgumentException("Tanggal pelepasan wajib diisi");
        }
        if (date.isBefore(asset.getPurchaseDate())) {
            throw new IllegalArgumentException("Tanggal pelepasan sebelum tanggal pembelian");
        }
        if (asset.getLastDepreciationDate() != null && date.isBefore(asset.getLastDepreciationDate())) {
            throw new IllegalArgumentException("Tanggal pelepasan sebelum penyusutan terakhir yang sudah diposting ("
                    + asset.getLastDepreciationDate() + ")");
        }
        assertAcquisitionPosted(asset);
        assertDepreciationSettled(asset, date);
    }

    private void assertAcquisitionPosted(FixedAsset asset) {
        boolean draftAcquisition = (asset.getPurchaseTransaction() != null
                && asset.getPurchaseTransaction().getStatus() == TransactionStatus.DRAFT)
                || transactionRepository.findBySourceDocumentTypeAndSourceDocumentIdOrderByTransactionDateAsc(
                        FixedAssetService.ACQUISITION_SOURCE_TYPE, asset.getId())
                .stream().anyMatch(Transaction::isDraft);
        if (draftAcquisition) {
            throw new IllegalStateException("Jurnal pembelian aset masih draf. Posting jurnal tersebut, "
                    + "atau hapus aset jika pembelian tidak jadi.");
        }
    }

    /**
     * The gain or loss needs a settled book value: no PENDING entry, and an entry of any status for every month
     * before the disposal month. The disposal month itself is left to the user.
     */
    private void assertDepreciationSettled(FixedAsset asset, LocalDate date) {
        List<DepreciationEntry> entries = depreciationEntryRepository.findByAssetId(asset.getId());
        if (entries.stream().anyMatch(DepreciationEntry::isPending)) {
            throw new IllegalStateException("Aset masih punya entri penyusutan PENDING. Posting atau skip lebih dulu.");
        }
        if (asset.isFullyDepreciated()) {
            return;
        }
        Set<YearMonth> covered = entries.stream().map(e -> YearMonth.from(e.getPeriodEnd())).collect(Collectors.toSet());
        YearMonth disposalMonth = YearMonth.from(date);
        for (YearMonth month = YearMonth.from(asset.getDepreciationStartDate()); month.isBefore(disposalMonth); month = month.plusMonths(1)) {
            if (!covered.contains(month)) {
                throw new IllegalStateException("Penyusutan bulan " + month + " belum dibuat. "
                        + "Generate dan posting penyusutan sampai bulan sebelum pelepasan.");
            }
        }
    }

    private ChartOfAccount resolveProceedsAccount(CreateDisposal command) {
        if (command.type() != DisposalType.SOLD) {
            if (command.proceedsAccountId() != null) {
                throw new IllegalArgumentException("Akun penerimaan hanya untuk penjualan");
            }
            return null;
        }
        if (command.proceedsAccountId() == null) {
            throw new IllegalArgumentException("Akun penerimaan wajib diisi untuk penjualan");
        }
        ChartOfAccount account = accountRepository.findById(command.proceedsAccountId())
                .orElseThrow(() -> new EntityNotFoundException("Akun penerimaan tidak ditemukan"));
        // A shareholder current account (issue #50) may receive proceeds paid to the shareholder or director;
        // the close checklist and the disposal record warn about it.
        boolean allowedType = account.getAccountType() == AccountType.ASSET
                || account.getCloseCheckRole() == com.artivisi.accountingfinance.enums.CloseCheckRole.SHAREHOLDER_CURRENT;
        if (!allowedType || Boolean.TRUE.equals(account.getIsHeader())
                || !Boolean.TRUE.equals(account.getActive())) {
            throw new IllegalArgumentException("Akun penerimaan harus akun aset atau rekening koran pemegang saham/direksi, "
                    + "aktif dan bukan akun induk: " + account.getAccountCode());
        }
        return account;
    }

    private Transaction composeDraft(AssetDisposal disposal, FixedAsset asset) {
        boolean sale = disposal.getDisposalType() == DisposalType.SOLD;
        BigDecimal gainLoss = disposal.getGainLoss();

        Map<String, UUID> hints = new HashMap<>();
        hints.put("AKUM_PENYUSUTAN", asset.getAccumulatedDepreciationAccount().getId());
        hints.put("ASET_TETAP", asset.getAssetAccount().getId());
        // A gift goes to the non-deductible account, so #46 fiscal drafts pick it up as a correction.
        hints.put("RUGI_PELEPASAN", accountByName(disposal.getDisposalType() == DisposalType.GIFTED
                ? NON_DEDUCTIBLE_ACCOUNT : LOSS_ACCOUNT).getId());
        if (sale) {
            hints.put("KAS_PENERIMAAN", disposal.getProceedsAccount().getId());
            hints.put("LABA_PELEPASAN", accountByName(GAIN_ACCOUNT).getId());
        }

        Map<String, BigDecimal> variables = new HashMap<>();
        variables.put("assetCost", disposal.getPurchaseCost());
        variables.put("accumulatedDepreciation", disposal.getAccumulatedDepreciation());
        variables.put("lossOnDisposal", gainLoss.signum() < 0 ? gainLoss.negate() : BigDecimal.ZERO);
        if (sale) {
            variables.put("disposalProceeds", disposal.getProceeds());
            variables.put("gainOnDisposal", gainLoss.signum() > 0 ? gainLoss : BigDecimal.ZERO);
        }

        return documentPostingService.createDraftFromTemplate(DocumentPostingService.DraftRequest.builder()
                .templateName(sale ? SALE_TEMPLATE : NON_SALE_TEMPLATE)
                .date(disposal.getDisposalDate())
                .description("Pelepasan " + asset.getAssetCode() + " - " + asset.getName() + " (" + disposal.getRecordNumber() + ")")
                .amount(disposal.getPurchaseCost())
                .hintToAccount(hints)
                .variables(variables)
                .createdBy(disposal.getCreatedBy())
                .sourceDocumentType(SOURCE_DOCUMENT_TYPE)
                .sourceDocumentId(disposal.getId())
                .build());
    }

    /** Codes differ per seed pack, so these accounts resolve by exact name. Zero or several matches fail. */
    private ChartOfAccount accountByName(String name) {
        List<ChartOfAccount> matches = accountRepository.findByAccountName(name);
        if (matches.size() != 1) {
            throw new IllegalStateException("Harus ada tepat satu akun bernama \"" + name + "\", ditemukan " + matches.size());
        }
        return matches.getFirst();
    }

    private String nextRecordNumber(int year) {
        TransactionSequence sequence = sequenceRepository.findBySequenceTypeAndYearForUpdate(SEQUENCE_TYPE, year)
                .orElseGet(() -> {
                    TransactionSequence created = new TransactionSequence();
                    created.setSequenceType(SEQUENCE_TYPE);
                    created.setPrefix(SEQUENCE_PREFIX);
                    created.setYear(year);
                    created.setLastNumber(0);
                    return sequenceRepository.save(created);
                });
        String number = sequence.getNextNumber();
        sequenceRepository.save(sequence);
        return number;
    }
}
