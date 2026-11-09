package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.AssetCondition;
import com.artivisi.accountingfinance.entity.AssetMovement;
import com.artivisi.accountingfinance.entity.AssetMovementSource;
import com.artivisi.accountingfinance.entity.FixedAsset;
import com.artivisi.accountingfinance.repository.AssetMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Objects;
import java.util.function.Supplier;

/** Applies custodian, location and condition to the register and keeps one row per change. */
@Component
@RequiredArgsConstructor
public class AssetMovementRecorder {

    private final AssetMovementRepository movementRepository;

    public void apply(FixedAsset asset, String custodian, String location, AssetCondition condition,
                      LocalDate date, AssetMovementSource source, Supplier<String> recordedBy) {
        String fromCustodian = normalize(asset.getCustodian());
        String fromLocation = normalize(asset.getLocation());
        AssetCondition fromCondition = asset.getCondition();
        String toCustodian = normalize(custodian);
        String toLocation = normalize(location);

        asset.setCustodian(toCustodian);
        asset.setLocation(toLocation);
        asset.setCondition(condition);

        if (Objects.equals(fromCustodian, toCustodian) && Objects.equals(fromLocation, toLocation)
                && fromCondition == condition) {
            return;
        }
        AssetMovement movement = new AssetMovement();
        movement.setFixedAsset(asset);
        movement.setMovementDate(date);
        movement.setSource(source);
        movement.setFromCustodian(fromCustodian);
        movement.setToCustodian(toCustodian);
        movement.setFromLocation(fromLocation);
        movement.setToLocation(toLocation);
        movement.setFromCondition(fromCondition);
        movement.setToCondition(condition);
        movement.setRecordedBy(recordedBy.get());
        movementRepository.save(movement);
    }

    static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
