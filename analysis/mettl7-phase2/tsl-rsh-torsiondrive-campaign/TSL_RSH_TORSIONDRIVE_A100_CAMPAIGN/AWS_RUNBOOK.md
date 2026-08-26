# AWS p4d PHI execution runbook

No command below authorizes CHI or PSI. PHI must complete and be reviewed before
a separately sealed PSI authorization is created.

## Instance and persistent storage

Launch an Ubuntu p4d.24xlarge with at least a 500 GiB gp3 EBS volume attached as
`/dev/nvme1n1`. Install the same frozen Python/CUDA scientific environment used
by the package. Mount persistent results at exactly:

```bash
sudo mkfs.ext4 /dev/nvme1n1   # only for a new, empty volume
sudo mkdir -p /mnt/tsl-rsh-ebs
sudo mount /dev/nvme1n1 /mnt/tsl-rsh-ebs
sudo chown -R "$USER":"$USER" /mnt/tsl-rsh-ebs
mkdir -p /mnt/tsl-rsh-ebs/torsiondrive
findmnt -T /mnt/tsl-rsh-ebs/torsiondrive
```

## Verify eight A100 GPUs

```bash
nvidia-smi --query-gpu=index,name,uuid,memory.total --format=csv,noheader
test "$(nvidia-smi --query-gpu=name --format=csv,noheader | wc -l)" -eq 8
test "$(nvidia-smi --query-gpu=name --format=csv,noheader | grep -c A100)" -eq 8
```

## Verify and launch PHI

```bash
sha256sum TSL_RSH_TORSIONDRIVE_AWS_8XA100_PHI.zip
unzip TSL_RSH_TORSIONDRIVE_AWS_8XA100_PHI.zip
cd TSL_RSH_TORSIONDRIVE_A100_CAMPAIGN
python test_package.py
python test_offline_replay.py
python test_multigpu_scheduler.py
python run_multigpu_aws.py --run-phi --workers 8 \
  --results-root /mnt/tsl-rsh-ebs/torsiondrive
```

Optional post-round S3 mirroring adds:

```bash
--s3-uri s3://YOUR-BUCKET/tsl-rsh/phi
```

## Status, interruption recovery, shutdown

```bash
python run_multigpu_aws.py --status \
  --results-root /mnt/tsl-rsh-ebs/torsiondrive

# Recovery uses the identical launch command; verified completed candidates are reused.
python run_multigpu_aws.py --run-phi --workers 8 \
  --results-root /mnt/tsl-rsh-ebs/torsiondrive

python run_multigpu_aws.py --verify-shutdown \
  --results-root /mnt/tsl-rsh-ebs/torsiondrive
```

Do not terminate the instance unless `SHUTDOWN_SAFE=true` is printed and the
EBS volume or configured S3 checkpoint is independently visible.
