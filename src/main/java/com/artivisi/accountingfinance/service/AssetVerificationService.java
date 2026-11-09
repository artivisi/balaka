package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.AssetCondition;
import com.artivisi.accountingfinance.entity.AssetMovementSource;
import com.artivisi.accountingfinance.entity.AssetStatus;
import com.artivisi.accountingfinance.entity.AssetVerification;
import com.artivisi.accountingfinance.entity.AssetVerificationFinding;
import com.artivisi.accountingfinance.entity.AssetVerificationResult;
import com.artivisi.accountingfinance.entity.FixedAsset;
import com.artivisi.accountingfinance.repository.AssetVerificationRepository;
import com.artivisi.accountingfinance.repository.FixedAssetRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Yearly physical check. The checklist of a year holds every asset bought by year end and not disposed by year end.
 * Exceptions are computed on read from the register and the year's result, so filling a missing field clears them.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AssetVerificationService {

    private final AssetVerificationRepository verificationRepository;
    private final FixedAssetRepository fixedAssetRepository;
    private final AssetMovementRecorder movementRecorder;

    public record RecordVerification(UUID assetId, int year, LocalDate date, AssetVerificationResult result,
                                     AssetCondition condition, String location, String custodian, String notes,
                                     String verifiedBy) {
    }

    public record ChecklistRow(FixedAsset asset, AssetVerification verification,
                               List<AssetVerificationFinding> exceptions) {
        public boolean hasExceptions() {
            return !exceptions.isEmpty();
        }
    }

    /**
     * FOUND writes condition, location and custodian to the register and moves the last verified date.
     * NOT_FOUND changes nothing on the register; the asset leaves the books only through the disposal flow.
     */
    public AssetVerification record(RecordVerification command) {
        FixedAsset asset = fixedAssetRepository.findById(command.assetId())
                .orElseThrow(() -> new EntityNotFoundException("Aset tidak ditemukan"));
        if (asset.isDisposed()) {
            throw new IllegalStateException("Aset yang sudah dilepas tidak diverifikasi");
        }
        if (command.result() == null) {
            throw new IllegalArgumentException("Hasil verifikasi wajib diisi");
        }
        if (command.date() == null || command.date().getYear() != command.year()) {
            throw new IllegalArgumentException("Tanggal verifikasi harus di tahun " + command.year());
        }
        if (command.date().isBefore(asset.getPurchaseDate())) {
            throw new IllegalArgumentException("Tanggal verifikasi sebelum tanggal pembelian");
        }

        AssetVerification verification = verificationRepository
                .findByFixedAssetIdAndVerificationYear(asset.getId(), command.year())
                .orElseGet(AssetVerification::new);
        verification.setFixedAsset(asset);
        verification.setVerificationYear(command.year());
        verification.setVerifiedDate(command.date());
        verification.setResult(command.result());
        verification.setNotes(AssetMovementRecorder.normalize(command.notes()));
        verification.setVerifiedBy(command.verifiedBy());

        if (command.result() == AssetVerificationResult.FOUND) {
            String location = AssetMovementRecorder.normalize(command.location());
            String custodian = AssetMovementRecorder.normalize(command.custodian());
            if (command.condition() == null || location == null || custodian == null) {
                throw new IllegalArgumentException("Aset yang ditemukan wajib diisi kondisi, lokasi, dan pemegangnya");
            }
            verification.setCondition(command.condition());
            verification.setLocation(location);
            verification.setCustodian(custodian);
            movementRecorder.apply(asset, custodian, location, command.condition(), command.date(),
                    AssetMovementSource.VERIFICATION, command::verifiedBy);
            if (asset.getLastVerifiedDate() == null || command.date().isAfter(asset.getLastVerifiedDate())) {
                asset.setLastVerifiedDate(command.date());
            }
        } else {
            verification.setCondition(null);
            verification.setLocation(null);
            verification.setCustodian(null);
        }
        return verificationRepository.save(verification);
    }

    @Transactional(readOnly = true)
    public List<ChecklistRow> checklist(int year) {
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        Map<UUID, AssetVerification> byAsset = verificationRepository.findByYear(year).stream()
                .collect(Collectors.toMap(v -> v.getFixedAsset().getId(), Function.identity()));
        return fixedAssetRepository.findAllWithCategory().stream()
                .filter(a -> !a.getPurchaseDate().isAfter(yearEnd))
                .filter(a -> a.getStatus() != AssetStatus.DISPOSED
                        || (a.getDisposalDate() != null && a.getDisposalDate().isAfter(yearEnd)))
                .map(a -> new ChecklistRow(a, byAsset.get(a.getId()), exceptionsOf(a, byAsset.get(a.getId()))))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChecklistRow> exceptions(int year) {
        return checklist(year).stream().filter(ChecklistRow::hasExceptions).toList();
    }

    @Transactional(readOnly = true)
    public List<AssetVerification> historyOf(UUID assetId) {
        return verificationRepository.findByAssetId(assetId);
    }

    private List<AssetVerificationFinding> exceptionsOf(FixedAsset asset, AssetVerification verification) {
        List<AssetVerificationFinding> found = new ArrayList<>();
        if (verification == null) {
            found.add(AssetVerificationFinding.NOT_VERIFIED);
        } else if (verification.getResult() == AssetVerificationResult.NOT_FOUND) {
            found.add(AssetVerificationFinding.NOT_FOUND);
        } else if (verification.getCondition() == AssetCondition.MAJOR_DAMAGE) {
            found.add(AssetVerificationFinding.MAJOR_DAMAGE);
        }
        if (isBlank(asset.getBrand()) || isBlank(asset.getModel())) {
            found.add(AssetVerificationFinding.MISSING_BRAND_MODEL);
        }
        if (isBlank(asset.getSerialNumber())) {
            found.add(AssetVerificationFinding.MISSING_SERIAL);
        }
        if (isBlank(asset.getCustodian())) {
            found.add(AssetVerificationFinding.MISSING_CUSTODIAN);
        }
        if (isBlank(asset.getLocation())) {
            found.add(AssetVerificationFinding.MISSING_LOCATION);
        }
        if (asset.getCondition() == null) {
            found.add(AssetVerificationFinding.MISSING_CONDITION);
        }
        if (asset.getPhotoDocument() == null) {
            found.add(AssetVerificationFinding.MISSING_PHOTO);
        }
        return found;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
