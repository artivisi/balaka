package com.artivisi.accountingfinance.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Controller
@RequestMapping("/about")
@Slf4j
public class AboutController {

    @GetMapping
    public String about(Model model) {
        // Get git commit ID
        String commitId = getGitCommitId();
        model.addAttribute("commitId", commitId);

        // Get git tag if available
        String gitTag = getGitTag();
        model.addAttribute("gitTag", gitTag);

        model.addAttribute("currentPage", "about");
        return "about";
    }

    private String getGitCommitId() {
        try {
            // Try to read from .git/HEAD
            Path gitHeadPath = Paths.get(".git/HEAD");
            if (Files.exists(gitHeadPath)) {
                String headContent = Files.readString(gitHeadPath).trim();
                
                // If HEAD points to a branch ref
                if (headContent.startsWith("ref: ")) {
                    String refPath = headContent.substring(5);
                    Path refFile = Paths.get(".git/" + refPath);
                    if (Files.exists(refFile)) {
                        return Files.readString(refFile).trim();
                    }
                } else {
                    // HEAD contains the commit hash directly (detached HEAD)
                    return headContent;
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read git commit ID: {}", e.getMessage());
        }
        return "unknown";
    }

    private String getGitTag() {
        try {
            // Use ProcessBuilder with explicit PATH to avoid PATH manipulation vulnerabilities
            // The git command is hardcoded, not user-controlled
            ProcessBuilder pb = new ProcessBuilder("git", "describe", "--tags", "--exact-match");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean completed = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);

            if (completed && process.exitValue() == 0) {
                return new String(process.getInputStream().readAllBytes()).trim();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("Git tag lookup interrupted");
        } catch (IOException e) {
            log.debug("No exact git tag found: {}", e.getMessage());
        }
        return null;
    }
}
