package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.Project;
import com.artivisi.accountingfinance.entity.ProjectMilestone;
import com.artivisi.accountingfinance.enums.MilestoneStatus;
import com.artivisi.accountingfinance.repository.ProjectMilestoneRepository;
import com.artivisi.accountingfinance.repository.ProjectRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectMilestoneService {

    private final ProjectMilestoneRepository milestoneRepository;
    private final ProjectRepository projectRepository;

    public ProjectMilestone findById(UUID id) {
        return milestoneRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Milestone not found with id: " + id));
    }

    public List<ProjectMilestone> findByProjectId(UUID projectId) {
        return milestoneRepository.findByProjectIdOrderBySequenceAsc(projectId);
    }

    public List<ProjectMilestone> findByProjectIdAndStatus(UUID projectId, MilestoneStatus status) {
        return milestoneRepository.findByProjectIdAndStatus(projectId, status);
    }

    @Transactional
    public ProjectMilestone create(UUID projectId, ProjectMilestone milestone) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found with id: " + projectId));

        // Auto-assign sequence if not provided
        if (milestone.getSequence() == null) {
            Integer maxSequence = milestoneRepository.findMaxSequenceByProjectId(projectId);
            milestone.setSequence(maxSequence + 1);
        } else {
            // Check if sequence already exists
            if (milestoneRepository.findByProjectIdAndSequence(projectId, milestone.getSequence()).isPresent()) {
                throw new IllegalArgumentException("Milestone with sequence " + milestone.getSequence() + " already exists");
            }
        }

        milestone.setProject(project);
        milestone.setStatus(MilestoneStatus.PENDING);
        return milestoneRepository.save(milestone);
    }

    @Transactional
    public ProjectMilestone update(UUID id, ProjectMilestone updatedMilestone) {
        ProjectMilestone existing = findById(id);

        // Check if sequence is being changed and already exists
        if (!existing.getSequence().equals(updatedMilestone.getSequence())) {
            if (milestoneRepository.findByProjectIdAndSequence(existing.getProject().getId(), updatedMilestone.getSequence()).isPresent()) {
                throw new IllegalArgumentException("Milestone with sequence " + updatedMilestone.getSequence() + " already exists");
            }
        }

        existing.setSequence(updatedMilestone.getSequence());
        existing.setName(updatedMilestone.getName());
        existing.setDescription(updatedMilestone.getDescription());
        existing.setWeightPercent(updatedMilestone.getWeightPercent());
        existing.setTargetDate(updatedMilestone.getTargetDate());

        return milestoneRepository.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        ProjectMilestone milestone = findById(id);
        milestoneRepository.delete(milestone);
    }

    @Transactional
    public void updateStatus(UUID id, MilestoneStatus newStatus) {
        ProjectMilestone milestone = findById(id);
        milestone.setStatus(newStatus);

        // Set actual date when completed
        if (newStatus == MilestoneStatus.COMPLETED) {
            milestone.setActualDate(LocalDate.now());
        } else {
            milestone.setActualDate(null);
        }

        milestoneRepository.save(milestone);
    }

    @Transactional
    public void startMilestone(UUID id) {
        updateStatus(id, MilestoneStatus.IN_PROGRESS);
    }

    @Transactional
    public void completeMilestone(UUID id) {
        updateStatus(id, MilestoneStatus.COMPLETED);
    }

    @Transactional
    public void resetMilestone(UUID id) {
        updateStatus(id, MilestoneStatus.PENDING);
    }

    public long countByProjectIdAndStatus(UUID projectId, MilestoneStatus status) {
        return milestoneRepository.countByProjectIdAndStatus(projectId, status);
    }

    public int getTotalWeightForProject(UUID projectId) {
        return findByProjectId(projectId).stream()
                .mapToInt(ProjectMilestone::getWeightPercent)
                .sum();
    }
}
