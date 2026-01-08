package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.config.TelegramConfig;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import com.artivisi.accountingfinance.dto.telegram.TelegramMessage;
import com.artivisi.accountingfinance.dto.telegram.TelegramPhotoSize;
import com.artivisi.accountingfinance.dto.telegram.TelegramUpdate;
import com.artivisi.accountingfinance.entity.Document;
import com.artivisi.accountingfinance.entity.DraftTransaction;
import com.artivisi.accountingfinance.entity.TelegramUserLink;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.repository.TelegramUserLinkRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.telegram.TelegramApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.net.URI;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class TelegramBotService {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final TelegramConfig config;
    private final TelegramUserLinkRepository telegramLinkRepository;
    private final UserRepository userRepository;
    private final DraftTransactionService draftService;
    private final DocumentService documentService;
    private TelegramApiClient telegramApiClient;

    public TelegramBotService(
            TelegramConfig config,
            TelegramUserLinkRepository telegramLinkRepository,
            UserRepository userRepository,
            DraftTransactionService draftService,
            DocumentService documentService,
            @Autowired(required = false) TelegramApiClient telegramApiClient) {
        this.config = config;
        this.telegramLinkRepository = telegramLinkRepository;
        this.userRepository = userRepository;
        this.draftService = draftService;
        this.documentService = documentService;
        this.telegramApiClient = telegramApiClient;

        log.info("TelegramBotService constructor - enabled: {}, apiClient: {}", 
                config.isEnabled(), telegramApiClient != null ? "present" : "null");
        
        if (config.isEnabled() && telegramApiClient != null) {
            log.info("Telegram bot initialized: @{}", config.getUsername());
        } else if (config.isEnabled() && telegramApiClient == null) {
            log.warn("Telegram is enabled but TelegramApiClient bean is not available. Check telegram.enabled property.");
        }
    }

    public void handleUpdate(TelegramUpdate update) {
        if (!config.isEnabled() || telegramApiClient == null) {
            log.warn("Telegram bot is not enabled");
            return;
        }

        TelegramMessage message = update.getMessage();
        if (message == null) {
            return;
        }

        Long chatId = message.getChat().getId();
        Long userId = message.getFrom().getId();
        String username = message.getFrom().getUsername();
        String firstName = message.getFrom().getFirstName();

        // Log with debug level to avoid exposing user IDs in production logs
        if (log.isDebugEnabled()) {
            log.debug("Received message from telegram user id: {}", sanitizeForLog(String.valueOf(userId)));
        }

        // Check if user is linked
        Optional<TelegramUserLink> linkOpt = telegramLinkRepository.findByTelegramUserIdAndIsActiveTrue(userId);
        if (log.isDebugEnabled()) {
            log.debug("User link status: {}", linkOpt.isPresent() ? "linked" : "not linked");
        }

        if (message.hasText()) {
            handleTextMessage(chatId, userId, username, firstName, message.getText(), linkOpt);
        } else if (message.hasPhoto()) {
            log.info("Processing photo message with {} photos", message.getPhoto().size());
            handlePhotoMessage(chatId, message.getPhoto(), message.getMessageId(), linkOpt);
        } else {
            log.info("Ignoring message - no text or photo");
        }
    }

    private void handleTextMessage(Long chatId, Long userId, String username, String firstName,
                                    String text, Optional<TelegramUserLink> linkOpt) {
        if (log.isDebugEnabled()) {
            log.debug("Processing text message from user id: {}", sanitizeForLog(String.valueOf(userId)));
        }

        if (text.startsWith("/start")) {
            handleStartCommand(chatId, userId, username, firstName, text);
        } else if (text.equals("/status")) {
            handleStatusCommand(chatId, linkOpt);
        } else if (text.equals("/help")) {
            handleHelpCommand(chatId);
        } else if (text.startsWith("/link ")) {
            handleLinkCommand(chatId, userId, username, firstName, text.substring(6).trim());
        } else {
            sendMessage(chatId, "Kirim foto struk untuk diproses, atau ketik /help untuk bantuan.");
        }
    }

    private void handleStartCommand(Long chatId, Long userId, String username, String firstName,
                                     String commandText) {
        Optional<TelegramUserLink> existingLink = telegramLinkRepository.findByTelegramUserId(userId);

        if (existingLink.isPresent() && existingLink.get().isLinked()) {
            sendMessage(chatId, String.format(
                    "Halo %s! Akun Telegram Anda sudah terhubung.%n%n" +
                    "Kirim foto struk untuk diproses.%n" +
                    "Ketik /status untuk melihat draft pending.%n" +
                    "Ketik /help untuk bantuan.", firstName));
            return;
        }

        // Check if there's a verification code in the command
        if (commandText.length() > 7) {
            String code = commandText.substring(7).trim();
            handleLinkCommand(chatId, userId, username, firstName, code);
            return;
        }

        sendMessage(chatId, String.format(
                "Selamat datang %s!%n%n" +
                "Untuk menghubungkan akun:%n" +
                "1. Login ke aplikasi akunting%n" +
                "2. Buka Pengaturan > Telegram%n" +
                "3. Klik 'Hubungkan Telegram'%n" +
                "4. Kirim kode verifikasi yang muncul dengan:%n" +
                "   /link KODE_ANDA%n%n" +
                "Atau klik link yang muncul di aplikasi.", firstName));
    }

    private void handleLinkCommand(Long chatId, Long userId, String username, String firstName, String code) {
        Optional<TelegramUserLink> linkByCode = telegramLinkRepository.findByVerificationCode(code);

        if (linkByCode.isEmpty()) {
            sendMessage(chatId, "Kode verifikasi tidak valid atau sudah kadaluarsa.");
            return;
        }

        TelegramUserLink link = linkByCode.get();

        if (link.isVerificationExpired()) {
            sendMessage(chatId, "Kode verifikasi sudah kadaluarsa. Silakan generate kode baru dari aplikasi.");
            return;
        }

        // Update link with Telegram info
        link.setTelegramUserId(userId);
        link.setTelegramUsername(username);
        link.setTelegramFirstName(firstName);
        link.setLinkedAt(LocalDateTime.now());
        link.setVerificationCode(null);
        link.setVerificationExpiresAt(null);
        link.setIsActive(true);
        telegramLinkRepository.save(link);

        sendMessage(chatId, String.format(
                "Akun berhasil terhubung! 🎉%n%n" +
                "Halo %s, sekarang Anda dapat:%n" +
                "• Kirim foto struk untuk diproses otomatis%n" +
                "• Ketik /status untuk cek draft pending%n%n" +
                "Kirim foto struk pertama Anda!", firstName));
    }

    private void handleStatusCommand(Long chatId, Optional<TelegramUserLink> linkOpt) {
        if (linkOpt.isEmpty()) {
            sendMessage(chatId, "Akun belum terhubung. Ketik /start untuk mulai.");
            return;
        }

        String username = linkOpt.get().getUser().getUsername();
        long pendingCount = draftService.countPendingByUser(username);

        if (pendingCount == 0) {
            sendMessage(chatId, "Tidak ada draft yang menunggu review. 👍");
        } else {
            sendMessage(chatId, String.format(
                    "Anda memiliki %d draft menunggu review.%n%n" +
                    "Buka aplikasi untuk mereview dan approve.", pendingCount));
        }
    }

    private void handleHelpCommand(Long chatId) {
        sendMessage(chatId, """
                📖 *Panduan Penggunaan*

                *Perintah:*
                /start - Mulai dan hubungkan akun
                /link KODE - Hubungkan dengan kode verifikasi
                /status - Cek jumlah draft pending
                /help - Tampilkan bantuan ini

                *Cara Pakai:*
                1. Pastikan akun sudah terhubung
                2. Kirim foto struk/bukti transfer
                3. Bot akan ekstrak data otomatis
                4. Review dan approve di aplikasi web

                *Format Struk yang Didukung:*
                • Bank Jago
                • CIMB OCTO
                • GoPay
                • BYOND/BSI
                • Struk umum lainnya
                """, true); // Use Markdown for formatted help
    }

    private void handlePhotoMessage(Long chatId, List<TelegramPhotoSize> photos,
                                     Long messageId, Optional<TelegramUserLink> linkOpt) {
        if (linkOpt.isEmpty()) {
            sendMessage(chatId, "Akun belum terhubung. Ketik /start untuk mulai.");
            return;
        }

        TelegramUserLink link = linkOpt.get();
        String appUsername = link.getUser().getUsername();

        // Get largest photo
        TelegramPhotoSize photo = photos.stream()
                .max(Comparator.comparingInt(p -> p.getWidth() * p.getHeight()))
                .orElse(null);

        if (photo == null) {
            sendMessage(chatId, "Gagal memproses foto. Silakan coba lagi.");
            return;
        }

        sendMessage(chatId, "⏳ Memproses struk...");

        try {
            // Download photo
            byte[] photoBytes = downloadPhoto(photo.getFileId());

            // Save as document
            Document document = documentService.saveFromBytes(
                    photoBytes, "receipt_" + messageId + ".jpg", "image/jpeg", appUsername);

            // Process receipt
            DraftTransaction draft = draftService.processReceiptImage(
                    photoBytes, document, chatId, messageId, appUsername);

            // Send result
            sendProcessingResult(chatId, draft);

        } catch (Exception e) {
            // Network/file errors are expected - log at warn without stack trace
            log.warn("Error processing photo for chat {}: {}", chatId, e.getMessage());
            sendMessage(chatId, "❌ Gagal memproses struk: " + e.getMessage());
        }
    }

    @SuppressFBWarnings(
        value = "URLCONNECTION_SSRF_FD",
        justification = "SSRF is prevented via domain validation: " +
                        "(1) URL is constructed with hardcoded 'https://api.telegram.org' domain. " +
                        "(2) File path comes from Telegram API response (getFile), not direct user input. " +
                        "(3) URL validation ensures it starts with expected Telegram domain. " +
                        "(4) Cannot be used to access internal services or arbitrary URLs."
    )
    private byte[] downloadPhoto(String fileId) throws Exception {
        var getFileRequest = new TelegramApiClient.GetFileRequest(fileId);
        var fileResponse = telegramApiClient.getFile(getFileRequest);

        if (!Boolean.TRUE.equals(fileResponse.ok()) || fileResponse.result() == null) {
            throw new RuntimeException("Failed to get file info: " + fileResponse.description());
        }

        String filePath = fileResponse.result().file_path();
        String fileUrl = String.format("https://api.telegram.org/file/bot%s/%s", config.getToken(), filePath);

        // Defense-in-depth: Validate that constructed URL actually points to Telegram API
        if (!fileUrl.startsWith("https://api.telegram.org/")) {
            throw new SecurityException("Invalid file URL: must be from api.telegram.org");
        }

        try (InputStream is = URI.create(fileUrl).toURL().openStream()) {
            return is.readAllBytes();
        }
    }

    private void sendProcessingResult(Long chatId, DraftTransaction draft) {
        StringBuilder sb = new StringBuilder();

        if (draft.getAmount() != null || draft.getMerchantName() != null) {
            sb.append("✅ *Struk Diterima*\n\n");

            if (draft.getMerchantName() != null) {
                sb.append("🏪 ").append(escapeMarkdown(draft.getMerchantName())).append("\n");
            }
            if (draft.getAmount() != null) {
                sb.append("💰 ").append(draft.getAmountFormatted()).append("\n");
            }
            if (draft.getTransactionDate() != null) {
                sb.append("📅 ").append(draft.getTransactionDate().format(DATE_FORMAT)).append("\n");
            }
            if (draft.getSuggestedTemplate() != null) {
                sb.append("📋 Template: ").append(escapeMarkdown(draft.getSuggestedTemplate().getTemplateName())).append("\n");
            }

            sb.append("\n📊 Confidence: ").append(draft.getConfidencePercentage());
            sb.append("\n\n_Buka aplikasi untuk review dan approve._");
        } else {
            sb.append("⚠️ *Struk Diterima* (data tidak lengkap)\n\n");
            sb.append("Gagal mengekstrak data dari struk.\n");
            sb.append("Buka aplikasi untuk input manual.");
        }

        sendMessage(chatId, sb.toString(), true); // Use Markdown for formatted result
    }

    private void sendMessage(Long chatId, String text) {
        sendMessage(chatId, text, false);
    }

    private void sendMessage(Long chatId, String text, boolean useMarkdown) {
        if (telegramApiClient == null) return;

        try {
            var request = new TelegramApiClient.SendMessageRequest(
                chatId, 
                text, 
                useMarkdown ? "Markdown" : null
            );
            var response = telegramApiClient.sendMessage(request);
            
            if (!Boolean.TRUE.equals(response.ok())) {
                log.error("Failed to send telegram message: {}", sanitizeForLog(response.description()));
            }
        } catch (Exception e) {
            log.error("Failed to send telegram message: {}", e.getMessage());
        }
    }

    private String escapeMarkdown(String text) {
        if (text == null) return "";
        return text.replace("_", "\\_")
                   .replace("*", "\\*")
                   .replace("[", "\\[")
                   .replace("`", "\\`");
    }

    /**
     * Sanitize user-controlled data for safe logging.
     * Removes newlines and limits length to prevent log injection attacks.
     */
    private String sanitizeForLog(String input) {
        if (input == null) {
            return "[null]";
        }
        // Remove newlines to prevent log injection
        String sanitized = input.replace("\n", " ").replace("\r", " ");
        // Limit length to prevent log flooding
        if (sanitized.length() > 100) {
            sanitized = sanitized.substring(0, 100) + "...";
        }
        return sanitized;
    }

    public String generateVerificationCode(User user) {
        String code = String.format("%06d", RANDOM.nextInt(1000000));

        Optional<TelegramUserLink> existingLink = telegramLinkRepository.findByUser(user);
        TelegramUserLink link;

        if (existingLink.isPresent()) {
            link = existingLink.get();
        } else {
            link = new TelegramUserLink();
            link.setUser(user);
        }

        link.setVerificationCode(code);
        link.setVerificationExpiresAt(LocalDateTime.now().plusMinutes(15));
        link.setIsActive(false);
        telegramLinkRepository.save(link);

        return code;
    }

    public boolean isEnabled() {
        return config.isEnabled() && telegramApiClient != null;
    }

    public String getBotUsername() {
        return config.getUsername();
    }
}
