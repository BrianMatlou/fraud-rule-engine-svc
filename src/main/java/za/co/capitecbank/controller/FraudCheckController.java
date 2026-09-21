package za.co.capitecbank.controller;

import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.capitecbank.dto.FraudCheckResponse;
import za.co.capitecbank.service.FraudCheckService;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/fraud")
public class FraudCheckController {

    private final FraudCheckService fraudCheckService;

    @GetMapping("/check/{transactionId}")
    public ResponseEntity<FraudCheckResponse> retrieveCheck(@PathVariable String transactionId){
        return ResponseEntity.ok(this.fraudCheckService.findCheckResults(transactionId));
    }

}
