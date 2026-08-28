#!/usr/bin/env python3
"""External cell-attempt watchdog contract.

The environment implementation is outside the scientific cell container.  A
timeout discards that entire environment; it never relies on signalling the
possibly uninterruptible QM process from inside the same kernel/container.
"""
from __future__ import annotations
import time

CELL_ATTEMPT_TIMEOUT_SECONDS = 20 * 60

def run_attempt(environment, timeout_seconds=CELL_ATTEMPT_TIMEOUT_SECONDS, poll_seconds=1.0, clock=time.monotonic, sleeper=time.sleep):
    started = clock()
    environment.start()
    while True:
        result = environment.completed_result()
        if result is not None:
            environment.verify_completed_result(result)
            return {"status": "COMPLETE", "result": result, "elapsed_seconds": clock() - started}
        elapsed = clock() - started
        if elapsed >= timeout_seconds:
            environment.persist_timeout_evidence(elapsed)
            environment.discard_entire_execution_environment()
            return {"status": "RUNTIME_TIMEOUT", "elapsed_seconds": elapsed}
        sleeper(min(poll_seconds, timeout_seconds - elapsed))

def run_cells(cells, environment_factory, completed_result, max_attempts=2, **watchdog_options):
    """Run cells sequentially while preserving verified completed work.

    Each retry receives a newly created environment.  Exhausting one cell does
    not prevent later benchmark cells from running.
    """
    outcomes = {}
    for cell in cells:
        existing = completed_result(cell)
        if existing is not None:
            outcomes[cell] = {"status": "REUSED_VERIFIED", "result": existing, "attempts": 0}
            continue
        attempts = []
        for attempt_index in range(max_attempts):
            environment = environment_factory(cell, attempt_index)
            outcome = run_attempt(environment, **watchdog_options)
            attempts.append(outcome)
            if outcome["status"] == "COMPLETE":
                outcomes[cell] = {**outcome, "attempts": len(attempts)}
                break
        else:
            outcomes[cell] = {"status": "RUNTIME_TIMEOUT", "attempts": len(attempts)}
    return outcomes
