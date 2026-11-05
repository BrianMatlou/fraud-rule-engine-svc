package za.co.capitecbank.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.capitecbank.dto.FraudCheckResponse;
import za.co.capitecbank.dto.TransactionEventDto;
import za.co.capitecbank.service.FraudCheckService;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/fraud")
public class FraudCheckController {

    private final FraudCheckService fraudCheckService;

    @PreAuthorize("hasAnyAuthority('FRAUD_ADMIN', 'FRAUD_CONSUMER')")
    @PostMapping("/check")
    public ResponseEntity<FraudCheckResponse> performCheck(@Valid @RequestBody TransactionEventDto request) {
        return ResponseEntity.ok(this.fraudCheckService.performCheck(request));
    }

    @PreAuthorize("hasAnyAuthority('FRAUD_ADMIN', 'FRAUD_ANALYST', 'FRAUD_CONSUMER')")
    @GetMapping("/check")
    public ResponseEntity<Page<FraudCheckResponse>> retrieveChecksByAccount(
            @RequestParam String accountId,
            @PageableDefault(size = 20, sort = "transactionTimestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(this.fraudCheckService.findChecksByAccountId(accountId, pageable));
    }

    @PreAuthorize("hasAnyAuthority('FRAUD_ADMIN', 'FRAUD_ANALYST', 'FRAUD_CONSUMER')")
    @GetMapping("/check/{transactionId}")
    public ResponseEntity<FraudCheckResponse> retrieveCheck(@PathVariable String transactionId){
        return ResponseEntity.ok(this.fraudCheckService.findCheckResults(transactionId));
    }

}
