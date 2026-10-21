# ADR-0007: Risk engine degradation policy

**Status:** accepted

## Decision
The risk engine is called synchronously with a 500 ms deadline over an eagerly connected gRPC channel. If it
times out or is unavailable:
- amounts below `bank.risk.fail-open-below-minor` (default 50.00) are approved with the signal
  `RISK_ENGINE_UNAVAILABLE` (fail-open for low value), and
- everything else is held for human review (fail-closed for high value).

Fallbacks are counted (`kuntur.risk.unavailable`) and alarmed on.

## Consequences
An outage of the risk engine never blocks small everyday payments and never lets a large payment through
unscored. Both behaviours are covered by `riskEngineOutageFailsOpenForSmallAndClosedForLargeAmounts`.
