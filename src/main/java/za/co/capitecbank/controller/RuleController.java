package za.co.capitecbank.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.capitecbank.dto.CreateRuleRequestDto;
import za.co.capitecbank.dto.RuleResponseDto;
import za.co.capitecbank.dto.UpdateRuleRequestDto;
import za.co.capitecbank.service.RuleService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rules")
@RequiredArgsConstructor
public class RuleController {

    private final RuleService ruleService;

    @PreAuthorize("hasAuthority('FRAUD_ADMIN')")
    @PostMapping("/create")
    public ResponseEntity<RuleResponseDto> createRule(@Valid @RequestBody CreateRuleRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ruleService.createRule(request));
    }

    @PreAuthorize("hasAnyAuthority('FRAUD_ADMIN', 'FRAUD_ANALYST')")
    @GetMapping
    public ResponseEntity<List<RuleResponseDto>> getAllRules() {
        return ResponseEntity.ok(ruleService.getAllRules());
    }

    @PreAuthorize("hasAnyAuthority('FRAUD_ADMIN', 'FRAUD_ANALYST')")
    @GetMapping("/{id}")
    public ResponseEntity<RuleResponseDto> getRuleById(@PathVariable UUID id) {
        return ResponseEntity.ok(ruleService.getRuleById(id));
    }

    @PreAuthorize("hasAuthority('FRAUD_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<RuleResponseDto> updateRule(@PathVariable UUID id, @Valid @RequestBody UpdateRuleRequestDto request) {
        return ResponseEntity.ok(ruleService.updateRule(id, request));
    }

    @PreAuthorize("hasAuthority('FRAUD_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id) {
        ruleService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }
}
