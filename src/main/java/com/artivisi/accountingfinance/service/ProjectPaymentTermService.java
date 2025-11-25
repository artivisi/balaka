package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.Project;
import com.artivisi.accountingfinance.entity.ProjectMilestone;
import com.artivisi.accountingfinance.entity.ProjectPaymentTerm;
import com.artivisi.accountingfinance.repository.ProjectMilestoneRepository;
import com.artivisi.accountingfinance.repository.ProjectPaymentTermRepository;
import com.artivisi.accountingfinance.repository.ProjectRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectPaymentTermService {

    private final ProjectPaymentTermRepository paymentTermRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMilestoneRepository milestoneRepository;

    public ProjectPaymentTerm findById(UUID id) {
        return paymentTermRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Payment term not found with id: " + id));
    }

    public List<ProjectPaymentTerm> findByProjectId(UUID projectId) {
        return paymentTermRepository.findByProjectIdOrderBySequenceAsc(projectId);
    }

    public List<ProjectPaymentTerm> findByMilestoneId(UUID milestoneId) {
        return paymentTermRepository.findByMilestoneId(milestoneId);
    }

    @Transactional
    public ProjectPaymentTerm create(UUID projectId, ProjectPaymentTerm paymentTerm) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found with id: " + projectId));

        // Auto-assign sequence if not provided
        if (paymentTerm.getSequence() == null) {
            Integer maxSequence = paymentTermRepository.findMaxSequenceByProjectId(projectId);
            paymentTerm.setSequence(maxSequence + 1);
        } else {
            // Check if sequence already exists
            if (paymentTermRepository.findByProjectIdAndSequence(projectId, paymentTerm.getSequence()).isPresent()) {
                throw new IllegalArgumentException("Payment term with sequence " + paymentTerm.getSequence() + " already exists");
            }
        }

        paymentTerm.setProject(project);

        // Link milestone if specified
        if (paymentTerm.getMilestone() != null && paymentTerm.getMilestone().getId() != null) {
            ProjectMilestone milestone = milestoneRepository.findById(paymentTerm.getMilestone().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Milestone not found"));
            paymentTerm.setMilestone(milestone);
        }

        return paymentTermRepository.save(paymentTerm);
    }

    @Transactional
    public ProjectPaymentTerm update(UUID id, ProjectPaymentTerm updatedPaymentTerm) {
        ProjectPaymentTerm existing = findById(id);

        // Check if sequence is being changed and already exists
        if (updatedPaymentTerm.getSequence() != null && !existing.getSequence().equals(updatedPaymentTerm.getSequence())) {
            if (paymentTermRepository.findByProjectIdAndSequence(existing.getProject().getId(), updatedPaymentTerm.getSequence()).isPresent()) {
                throw new IllegalArgumentException("Payment term with sequence " + updatedPaymentTerm.getSequence() + " already exists");
            }
            existing.setSequence(updatedPaymentTerm.getSequence());
        }

        existing.setName(updatedPaymentTerm.getName());
        existing.setPercentage(updatedPaymentTerm.getPercentage());
        existing.setAmount(updatedPaymentTerm.getAmount());
        existing.setDueTrigger(updatedPaymentTerm.getDueTrigger());
        existing.setDueDate(updatedPaymentTerm.getDueDate());

        // Update milestone link
        if (updatedPaymentTerm.getMilestone() != null && updatedPaymentTerm.getMilestone().getId() != null) {
            ProjectMilestone milestone = milestoneRepository.findById(updatedPaymentTerm.getMilestone().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Milestone not found"));
            existing.setMilestone(milestone);
        } else {
            existing.setMilestone(null);
        }

        return paymentTermRepository.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        ProjectPaymentTerm paymentTerm = findById(id);
        paymentTermRepository.delete(paymentTerm);
    }
}
