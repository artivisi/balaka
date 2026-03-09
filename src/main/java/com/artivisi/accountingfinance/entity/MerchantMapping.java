package com.artivisi.accountingfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Entity
@Table(name = "merchant_mappings")
@Getter
@Setter
@NoArgsConstructor
public class MerchantMapping extends TimestampedEntity {

    public enum MatchType {
        EXACT, CONTAINS, REGEX
    }

    @NotBlank(message = "Merchant pattern is required")
    @Size(max = 255, message = "Merchant pattern must not exceed 255 characters")
    @Column(name = "merchant_pattern", nullable = false, length = 255)
    private String merchantPattern;

    @NotNull(message = "Match type is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 20)
    private MatchType matchType = MatchType.CONTAINS;

    @NotNull(message = "Template is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_template", nullable = false)
    private JournalTemplate template;

    @Size(max = 500, message = "Default description must not exceed 500 characters")
    @Column(name = "default_description", length = 500)
    private String defaultDescription;

    @Column(name = "match_count", nullable = false)
    private Integer matchCount = 0;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Size(max = 100)
    @Column(name = "created_by", length = 100)
    private String createdBy;

    public boolean matches(String merchantName) {
        if (merchantName == null || merchantPattern == null) {
            return false;
        }

        return switch (matchType) {
            case EXACT -> merchantName.equalsIgnoreCase(merchantPattern);
            case CONTAINS -> merchantName.toLowerCase().contains(merchantPattern.toLowerCase());
            case REGEX -> Pattern.compile(merchantPattern, Pattern.CASE_INSENSITIVE)
                    .matcher(merchantName).find();
        };
    }

    public void incrementMatchCount() {
        this.matchCount++;
        this.lastUsedAt = LocalDateTime.now();
    }
}
