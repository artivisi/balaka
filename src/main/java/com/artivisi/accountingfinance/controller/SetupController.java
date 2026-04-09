package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.DataImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * First-run setup wizard. Active only when the users table is empty.
 * Creates the first admin user and loads the selected industry seed pack.
 * Once a user exists, this controller redirects to /login.
 */
@Controller
@RequestMapping("/setup")
@RequiredArgsConstructor
@Slf4j
public class SetupController {

    public static final List<String> AVAILABLE_PACKS =
            List.of("it-service", "online-seller", "coffee-shop", "campus");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DataImportService dataImportService;

    private final PathMatchingResourcePatternResolver resourceResolver =
            new PathMatchingResourcePatternResolver();

    @GetMapping
    public String form(Model model) {
        if (userRepository.count() > 0) {
            return "redirect:/login";
        }
        model.addAttribute("packs", AVAILABLE_PACKS);
        return "setup";
    }

    @PostMapping
    @Transactional
    public String submit(@RequestParam String username,
                         @RequestParam String password,
                         @RequestParam String fullName,
                         @RequestParam(required = false) String email,
                         @RequestParam String industryPack,
                         RedirectAttributes ra) throws IOException {
        if (userRepository.count() > 0) {
            return "redirect:/login";
        }
        if (!AVAILABLE_PACKS.contains(industryPack)) {
            throw new IllegalArgumentException("Unknown industry pack: " + industryPack);
        }

        // Load seed pack first — if classpath resources are missing, fail before
        // creating the admin user so the wizard can be retried.
        byte[] zip = buildSeedZip(industryPack);
        DataImportService.ImportResult result = dataImportService.importAllData(zip);
        log.info("Setup wizard imported seed pack {}: {} records in {}ms",
                industryPack, result.totalRecords(), result.durationMs());

        User admin = new User();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setFullName(fullName);
        admin.setEmail(email != null && !email.isBlank() ? email : null);
        admin.setActive(true);
        admin.setRoles(Set.of(Role.ADMIN), "setup-wizard");
        userRepository.save(admin);
        log.info("Setup wizard created admin user: {}", username);

        ra.addFlashAttribute("setupComplete", true);
        return "redirect:/login";
    }

    private byte[] buildSeedZip(String pack) throws IOException {
        String pattern = "classpath:seed-packs/" + pack + "/seed-data/**/*.csv";
        Resource[] resources = resourceResolver.getResources(pattern);
        if (resources.length == 0) {
            throw new IOException("No seed CSV files found on classpath for pack: " + pack
                    + " (pattern: " + pattern + ")");
        }
        String prefix = "seed-packs/" + pack + "/seed-data/";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Resource res : resources) {
                String url = res.getURL().toString();
                int idx = url.indexOf(prefix);
                if (idx < 0) {
                    throw new IOException("Unexpected resource path: " + url);
                }
                String entryName = url.substring(idx + prefix.length());
                ZipEntry entry = new ZipEntry(entryName);
                zos.putNextEntry(entry);
                try (var in = res.getInputStream()) {
                    in.transferTo(zos);
                }
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
}
