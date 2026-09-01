# Dormant opener probe notes

The policy derives low evidence from the discounted games available immediately before an opener re-enters. It does not add learning-file fields, so historical CSV files remain compatible. Forced and organic dormant re-entries use the same classification, trial, exposure, and cooldown accounting.

`PROBE_LOW_EVIDENCE_GAMES` is 3.0 because fewer than three discounted observations cannot outweigh a single recent result reliably. `PROBE_BURST_GAMES` is 3, matching the existing three-game trial. `PROBE_EXPOSURE_FRACTION` is 0.15 over `PROBE_EXPOSURE_WINDOW_GAMES` of 20, allowing at most three low-evidence games in the recent window. This preserves one complete trial while bounding total exposure.

The burst remains blocked after dormancy when a low-evidence trial has fewer than two wins, so a single win cannot restore ordinary argmax eligibility. Every game in an unpromoted low-evidence trial counts toward the exposure budget, including organic re-entries. Organic re-entries therefore receive consistent treatment without indefinitely starving an opener that has not yet been probed. Promoted trials stop consuming probe cooldown and exposure.

The closed-loop tests measure long-run dormant exposure, total selections after one probe win through the former demotion-lift boundary, and selection of 12Hatch after an organic 12Pool re-entry. The existing tests continue to cover timely probing, the winning-leader gate, repeated probes, bounded exploration, and promotion followed by renewed probing.

Verification: `mvn -o package` passed 234 tests, and `mvn -o checkstyle:check` passed with no violations.
