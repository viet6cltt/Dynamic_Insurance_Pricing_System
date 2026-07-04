package com.insurance.applicationpolicyservice.service;

import com.insurance.applicationpolicyservice.dto.ContractStatus;
import com.insurance.applicationpolicyservice.model.InsuranceContract;
import com.insurance.applicationpolicyservice.repository.InsuranceContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractExpirationService {

    private final InsuranceContractRepository contractRepository;
    private final ContractService contractService;
    private final PolicyEventFactory policyEventFactory;
    private final PolicyOutboxService policyOutboxService;

    @Value("${app.policy.expiry-reminder-days:7}")
    private int expiryReminderDays;

    @Scheduled(cron = "${app.policy.expiration-cron:0 5 0 * * *}")
    @Transactional
    public int sendExpiryReminders() {
        LocalDate targetDate = LocalDate.now().plusDays(expiryReminderDays);
        List<InsuranceContract> contracts = contractRepository.findContractsNeedingExpiryReminderForUpdate(targetDate);

        for (InsuranceContract contract : contracts) {
            contract.setExpiryReminderSentAt(Instant.now());
            InsuranceContract saved = contractRepository.save(contract);
            policyOutboxService.enqueue(policyEventFactory.createContractExpiryReminderEvent(
                    saved,
                    expiryReminderDays,
                    null,
                    null
            ));
        }

        if (!contracts.isEmpty()) {
            log.info("Queued {} expiry reminders for contracts expiring on {}", contracts.size(), targetDate);
        }
        return contracts.size();
    }

    @Scheduled(cron = "${app.policy.expiration-cron:0 5 0 * * *}")
    @Transactional
    public int expireActiveContracts() {
        LocalDate today = LocalDate.now();
        List<InsuranceContract> expiredContracts = contractRepository.findExpiredActiveContractsForUpdate(today);

        for (InsuranceContract contract : expiredContracts) {
            contract.setStatus(ContractStatus.EXPIRED);
            InsuranceContract saved = contractRepository.save(contract);
            contractService.updateExperienceSummary(saved);
            policyOutboxService.enqueue(policyEventFactory.createContractEvent(
                    saved,
                    "contract.expired",
                    null,
                    null
            ));
        }

        if (!expiredContracts.isEmpty()) {
            log.info("Expired {} active contracts with expiryDate before {}", expiredContracts.size(), today);
        }
        return expiredContracts.size();
    }
}
