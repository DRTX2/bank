package com.drtx.bank.ops;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.drtx.bank.shared.security.CurrentUser;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ops")
class OpsController {

    private final OpsService ops;
    private final TrafficSimulator simulator;

    OpsController(OpsService ops, TrafficSimulator simulator) {
        this.ops = ops;
        this.simulator = simulator;
    }

    record SimulationRequest(@Min(1) @Max(1000) int transfers, @DecimalMin("0") @DecimalMax("0.5") double fraudRatio,
                             @Min(0) @Max(2000) int delayMs) {
    }

    record RunView(UUID id, int requested, int processed, int completed, int held, int rejected, int failed,
                   boolean finished) {
        static RunView of(TrafficSimulator.Run r) {
            return new RunView(r.id(), r.requested(), r.processed().get(), r.completed().get(), r.held().get(),
                    r.rejected().get(), r.failed().get(), r.finished());
        }
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('ops:monitor')")
    OpsService.Dashboard dashboard() {
        return ops.dashboard();
    }

    @PostMapping("/simulator/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('ops:simulate')")
    RunView start(CurrentUser user, @Valid @RequestBody SimulationRequest body) {
        return RunView.of(simulator.start(user, body.transfers(), body.fraudRatio(), body.delayMs()));
    }

    @GetMapping("/simulator/runs/{id}")
    @PreAuthorize("hasAuthority('ops:simulate')")
    RunView run(@PathVariable UUID id) {
        return RunView.of(simulator.get(id));
    }
}
