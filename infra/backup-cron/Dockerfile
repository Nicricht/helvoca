# Cron image only. Do not attach this to Railway source or set a schedule until
# operator configures institutional OAuth, age recipient and approves runtime cost.
FROM postgres:18-bookworm

RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 ca-certificates age rclone \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt/helvoca
COPY scripts/ops /opt/helvoca/scripts/ops

# Safe even if someone accidentally connects the image to an empty service:
# this default does NOT touch production or Google Drive.
CMD ["python3", "-c", "print('C1_CRON_PREPARED_NOT_ENABLED')"]
