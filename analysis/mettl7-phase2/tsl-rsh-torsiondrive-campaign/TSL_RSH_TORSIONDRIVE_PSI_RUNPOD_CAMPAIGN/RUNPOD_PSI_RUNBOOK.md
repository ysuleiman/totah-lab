# RunPod PSI-only execution gate

Do not launch until an exact A100 pod and persistent volume have been verified.
The only execution command is:

```bash
python run_multigpu_psi.py --run-psi --workers 2 \
  --results-root /workspace/tsl-rsh/torsiondrive/results
```

There is no PHI or CHI execution entrypoint. Download and checksum the sealed
result archive before stopping and deleting paid RunPod resources.
